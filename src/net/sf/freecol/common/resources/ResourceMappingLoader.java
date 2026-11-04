/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.resources;

import static net.sf.freecol.common.util.StringUtils.join;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.freecol.common.MemoryManager;
import net.sf.freecol.common.io.FreeColDataFile;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColTcFile;
import net.sf.freecol.common.util.LogBuilder;


/**
 * Builds the {@code ResourceMapping} (images, sounds, fonts...) for a
 * FreeCol data file.
 *
 * This used to be part of {@code FreeColDataFile}, but resources are
 * only needed by the desktop client, so it was split out to keep the
 * shared engine free of the image and sound code.
 */
public final class ResourceMappingLoader {

    private static final Logger logger
        = Logger.getLogger(ResourceMappingLoader.class.getName());

    /** A fake URI scheme for resources delegating to other resources. */
    private static final String resourceScheme = "resource:";


    private ResourceMappingLoader() {} // Static only

    /**
     * Creates a {@code ResourceMapping} for a data file.
     *
     * For a total conversion the resources of its parent are included
     * first, so that the local values can override them.
     *
     * @param data The {@code FreeColDataFile} to read.
     * @return A {@code ResourceMapping}, or {@code null} if a plain
     *     data file has no resource mapping file.
     */
    public static ResourceMapping load(FreeColDataFile data) {
        if (!(data instanceof FreeColTcFile)) return loadOwn(data);

        final FreeColTcFile tc = (FreeColTcFile)data;
        ResourceMapping result = new ResourceMapping();
        if (tc.getParent() != null) {
            final FreeColTcFile parentTcData
                = FreeColTcFile.getFreeColTcFile(tc.getParent());
            result.addAll(load(parentTcData));
        }
        // Add the local data *after* the parent data so that the local
        // values can override (eventual call is Map.putAll).
        // Note that loadOwn logs the load, and thus the log messages
        // will appear to be in the reverse order.
        result.addAll(loadOwn(tc));
        return result;
    }

    /**
     * Creates a {@code ResourceMapping} from the available resource files.
     *
     * @return A {@code ResourceMapping} or {@code null}
     *     there is no resource mapping file.
     */
    private static ResourceMapping loadOwn(final FreeColDataFile data) {
        final LogBuilder lb = new LogBuilder(64);
        lb.add("Resource mappings:");
        lb.mark();
        final Properties properties = readResourcesProperties(data, lb);
        if (properties == null) {
            return null;
        }

        final ResourceMapping rc = new ResourceMapping();
        final List<String> virtualResources = handleResources(data, properties, rc);
        handleVirtualResources(data, virtualResources, lb, properties, rc);
        
        if (lb.grew()) lb.log(logger, Level.FINE);
        return rc;
    }

    /**
     * Handles loading of all resources defined in {@code properties}.
     * 
     * This method also handles alternate variations and sizes.
     *  
     * @param properties The resources to be loaded.
     * @param rc The output object where resolved resources should be placed.
     * @return A list of keys that are virtual resources, that is a link to
     *      another resource. Virtual resources are used by mods when the
     *      author wants to reuse graphics that are defined in other FreeCol
     *      data files.
     */
    private static List<String> handleResources(final FreeColDataFile data, final Properties properties, ResourceMapping rc) {
        final ResourceFactory resourceFactory = new ResourceFactory();
        final List<String> virtualResources = new ArrayList<>();
        final Enumeration<?> pn = properties.propertyNames();
        while (pn.hasMoreElements()) {
            final String key = (String) pn.nextElement();
            
            /*
             * We are stripping ".r0" from the end of keys in order to
             * support old keys where the variant was listed in
             * resources.properties.
             */
            final String updatedKey = stripEnding(key, ".r0");
            final String value = properties.getProperty(key);
            
            if (value.startsWith(resourceScheme)) {
                virtualResources.add(updatedKey);
            } else {
                handleNormalResource(data, resourceFactory, rc, key, value);
            }
        }
        return virtualResources;
    }

    private static void handleNormalResource(final FreeColDataFile data, ResourceFactory resourceFactory, ResourceMapping rc, final String key, final String value) {
        final URI uri = data.getURI(value);
        if (uri == null) {
            return;
        }
        
        /*
         * The caching key should be (and only be) the same when it's the
         * same actual resource data (for example, the same image file).
         * 
         * Please note that the key is not suitable for a caching key,
         * since the resource might get replaced by a mod.
         */
        final String cachingKey = uri.toString();
        final Resource resource = resourceFactory.createResource(key, cachingKey, uri);
        
        /*
         * Rivers need new keys in order to support variations.
         */
        final boolean supportsVariations = !key.contains(".improvement.river.");
        
        if (resource instanceof ImageResource && supportsVariations) {
            final ImageResource imageResource = (ImageResource) resource;
            extendWithAdditionalSizesAndVariations(data, resourceFactory, imageResource, value);
        }
        
        if (resource != null) {
            rc.add(key, resource);
        }
    }

    private static void handleVirtualResources(final FreeColDataFile data, List<String> virtualResources, final LogBuilder lb, final Properties properties, ResourceMapping rc) {
        boolean progress = true;
        List<String> miss = new ArrayList<>();
        while (progress && !virtualResources.isEmpty()) {
            miss.clear();
            progress = false;
            while (!virtualResources.isEmpty()) {
                final String key = virtualResources.remove(0);
                /*
                 * We are stripping ".r0" from the end of keys in order to
                 * support old keys where the variant was listed in
                 * resources.properties.
                 */
                final String updatedKey = stripEnding(key, ".r0");
                
                final String value = properties.getProperty(key)
                    .substring(resourceScheme.length());
                if (!rc.duplicateResource(value, updatedKey)) {
                    miss.add(key);
                } else {
                    progress = true;
                }
            }
            virtualResources.addAll(miss);
        }
        if (!virtualResources.isEmpty()) {
            lb.add(", could not resolve virtual resource/s: ",
                   join(" ", virtualResources));
        }
    }

    /**
     * Reads "resources.properties". We support localized versions of this
     * file, so that localized images can be provided.
     * 
     * @param lb Just used for logging.
     * @return The read properties, or {@code null} if an error occured while
     *      reading the file(s).
     */
    private static Properties readResourcesProperties(final FreeColDataFile data, final LogBuilder lb) {
        final Properties properties = new Properties();
        for (String fileName : FreeColDirectories.getResourceFileNames()) {
            try (
                final InputStream is = data.getInputStream(fileName);
            ) {
                properties.load(is);
                lb.add(' ', data.getFile(), '/', fileName, ":ok");
            } catch (FileNotFoundException e) { // Expected failure
                lb.add(' ', data.getFile(), '/', fileName, ":not-found");
            } catch (IOException e) {
                logger.log(Level.WARNING, "ResourceMapping read exception: "
                    + data.getFile() + "/" + fileName, e);
                return null;
            }
        }
        return properties;
    }
    
    private static String stripEnding(String key, String ending) {
        return (key.endsWith(ending)) ? key.substring(0, key.length() - 3) : key;
    }
    
    private static void extendWithAdditionalSizesAndVariations(final FreeColDataFile data, ResourceFactory resourceFactory, ImageResource imageResource, String value) {
        Map<URI, List<URI>> variationsWithAlternateSizes = findVariationsWithAlternateSizes(data, value);
        imageResource.addAlternativeResourceLocators(variationsWithAlternateSizes.get(null));
        
        variationsWithAlternateSizes.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null)
            .forEach(entry -> {
                final ImageResource variationResource = (ImageResource) resourceFactory.createResource("", imageResource.getCachingKey(), entry.getKey());
                if (variationResource != null) {
                    variationResource.addAlternativeResourceLocators(entry.getValue());
                    imageResource.addVariation(variationResource);
                }
            });
    }

    private static Map<URI, List<URI>> findVariationsWithAlternateSizes(final FreeColDataFile data, String name) {
        if (name.indexOf(".") <= 0) {
            return Map.of();
        }

        FileSystem fileSystem = null;
        try {
            final Path filePath;
            if (data.getFile().isDirectory()) {
                filePath = new File(data.getFile(), name).toPath();
            } else {
                /*
                 * We can use JarEntry instead, if this solution causes problems. 
                 */
                fileSystem = FileSystems.newFileSystem(new URI("jar:file", data.getFile().getAbsolutePath(), null), Map.of());
                filePath = fileSystem.getPath(data.getJarDirectory() + name);
            }
            
            /*
             * Using LinkedHashMap to ensure we keep the variations in order.
             */
            final Map<URI, List<URI>> result = new LinkedHashMap<>();
            if (MemoryManager.isHighQualityGraphicsEnabled()) {
                result.put(null, findFilesWithVariationOrAlternativeSizeAsUri(data, filePath, false));
            } else {
                result.put(null, List.of());
            }
            
            final List<Path> variations = findFilesWithVariationOrAlternativeSize(data, filePath, true);
            for (Path variationPath : variations) {
                if (MemoryManager.isHighQualityGraphicsEnabled()) {
                    result.put(variationPath.toUri(), findFilesWithVariationOrAlternativeSizeAsUri(data, variationPath, false));
                } else {
                    result.put(variationPath.toUri(), List.of());
                }
            } 
            
            return result;
        } catch (IOException | URISyntaxException e) {
            logger.log(Level.WARNING, "Failed to read directory from jar/zip file: " + data.getFile() + " jarDirectory" + data.getJarDirectory(), e);
            return Map.of();
        } finally {
            if (fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException e) {}
            }
        }
    }

    private static List<URI> findFilesWithVariationOrAlternativeSizeAsUri(final FreeColDataFile data, final Path filePath, boolean findVariation) throws IOException {
        return toUris(findFilesWithVariationOrAlternativeSize(data, filePath, findVariation));
    }
    
    private static List<URI> toUris(List<Path> paths) {
        return paths.stream()
                .map(p -> p.toUri())
                .collect(Collectors.toList());
    }
    
    private static List<Path> findFilesWithVariationOrAlternativeSize(final FreeColDataFile data, final Path filePath, boolean findVariation) throws IOException {
        final String variationFileRegex = "[0-9][0-9]?";
        final String sizeFileRegex = "\\.size[0-9][0-9]*";
        
        final String regex = (findVariation) ? variationFileRegex : sizeFileRegex;       
        final String resourceFilename = filePath.getFileName().toString();
        String prefix = resourceFilename.substring(0, resourceFilename.lastIndexOf("."));
        if (findVariation) {
            prefix = prefix.replaceAll("[0-9]*$", "");
        }
        final String suffix = resourceFilename.substring(resourceFilename.lastIndexOf("."));
        final String completeRegex = Pattern.quote(prefix) + regex + Pattern.quote(suffix);
        
        try (Stream<Path> pathStream = Files.list(filePath.getParent())) {
            return pathStream
                    .sorted()
                    .filter(p -> p.getFileName().toString().matches(completeRegex) && (!findVariation || !p.equals(filePath)))
                    .collect(Collectors.toList());
        }
    }

}
