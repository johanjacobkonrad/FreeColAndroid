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
package net.sf.freecol.android;

import java.io.IOException;
import java.io.InputStream;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.sf.freecol.android.map.Art;


/**
 * FreeCol's artwork from the app's "art" assets (a subset of
 * data/default, with the base data's terrain masks under "art/base";
 * see app/build.gradle), decoded as Android bitmaps.
 */
public final class AndroidArt extends Art {

    private static final String ROOT = "art/";

    private final AssetManager assets;


    public AndroidArt(AssetManager assets) throws IOException {
        this.assets = assets;
        loadKeys();
    }

    @Override
    protected InputStream open(String path) throws IOException {
        return assets.open(ROOT + path);
    }

    @Override
    protected String[] list(String dir) throws IOException {
        return assets.list(ROOT + dir);
    }

    @Override
    protected Img applyMask(Img image, Img mask) {
        Bitmap src = (Bitmap)image.handle;
        Bitmap out = Bitmap.createBitmap(image.width, image.height,
                                         Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawBitmap(src, 0, 0, null);
        android.graphics.Paint p = new android.graphics.Paint(
            android.graphics.Paint.FILTER_BITMAP_FLAG);
        p.setXfermode(new android.graphics.PorterDuffXfermode(
            android.graphics.PorterDuff.Mode.DST_IN));
        c.drawBitmap((Bitmap)mask.handle, null,
            new android.graphics.Rect(0, 0, image.width, image.height), p);
        return new Img(out, image.width, image.height);
    }

    @Override
    protected Img decode(InputStream in) throws IOException {
        Bitmap b = BitmapFactory.decodeStream(in);
        return (b == null) ? null : new Img(b, b.getWidth(), b.getHeight());
    }
}
