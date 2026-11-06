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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;


/**
 * The game's music and sounds.  Music: FreeCol's tracks (made smaller
 * by android/encode-music.sh) in a shuffled loop while a screen of the
 * app is showing.  Sounds: by their key in FreeCol's resources
 * ("sound.event.loadCargo"); short ones through a sound pool, the
 * longer "music" ones (anthems, the Fountain of Youth) through a
 * player of their own, with the music turned down meanwhile.
 * Volumes come from {@link Settings}.
 */
final class Audio {

    private static final Logger logger = Logger.getLogger(Audio.class.getName());
    private static Audio instance = null;

    private final Context app;
    private final Properties keys = new Properties();
    private final AudioAttributes attributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final SoundPool pool;
    private final Map<String, Integer> ids = new HashMap<>();
    private final Set<Integer> ready = new HashSet<>();
    private final Set<Integer> playWhenReady = new HashSet<>();
    private final List<String> tracks = new ArrayList<>();
    private int track = 0;
    private MediaPlayer music = null, jingle = null;
    /** Screens showing that want the music. */
    private int listeners = 0;


    static synchronized Audio get(Context c) {
        if (instance == null) instance = new Audio(c.getApplicationContext());
        return instance;
    }

    private Audio(Context app) {
        this.app = app;
        try (InputStream in = app.getAssets().open("art/resources.properties")) {
            keys.load(in);
        } catch (Exception e) {
            logger.log(Level.WARNING, "No sound keys", e);
        }
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
                synchronized (this) {
                    if (status != 0) return;
                    ready.add(id);
                    if (playWhenReady.remove(id)) playLoaded(id);
                }
            });
        try {
            String[] files = app.getAssets().list("music");
            if (files != null) {
                for (String f : files) if (f.endsWith(".ogg")) tracks.add("music/" + f);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "No music", e);
        }
        Collections.shuffle(tracks);
    }


    // Music

    /** A screen that wants the music is showing. */
    synchronized void startMusic() {
        listeners++;
        updateMusic();
    }

    /** That screen has gone. */
    synchronized void stopMusic() {
        listeners = Math.max(0, listeners - 1);
        updateMusic();
    }

    /** The music volume setting changed. */
    synchronized void musicVolumeChanged() {
        updateMusic();
    }

    private float musicVolume() {
        return Settings.music(app) / 100f;
    }

    private void updateMusic() {
        float v = musicVolume();
        if (listeners == 0 || v <= 0 || tracks.isEmpty()) {
            if (music != null && music.isPlaying()) music.pause();
            return;
        }
        if (music == null) {
            playTrack();
        } else {
            float duck = (jingle != null) ? 0.25f : 1f;
            music.setVolume(v * duck, v * duck);
            if (!music.isPlaying()) music.start();
        }
    }

    private void playTrack() {
        if (tracks.isEmpty()) return;
        String path = tracks.get(track % tracks.size());
        track++;
        MediaPlayer mp = player(path);
        if (mp == null) return;
        float v = musicVolume();
        mp.setVolume(v, v);
        mp.setOnCompletionListener(done -> {
                synchronized (this) {
                    done.release();
                    if (music == done) music = null;
                    if (listeners > 0) playTrack();
                }
            });
        music = mp;
        mp.start();
    }

    private MediaPlayer player(String asset) {
        try (AssetFileDescriptor fd = app.getAssets().openFd(asset)) {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attributes);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.prepare();
            return mp;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Can not play " + asset, e);
            return null;
        }
    }


    // Sounds

    /**
     * Play a sound by its key; nothing if it is unknown or effects are
     * off.
     *
     * @param key The key, as in FreeCol's resources.properties.
     */
    synchronized void play(String key) {
        String path = keys.getProperty(key);
        if (path == null || Settings.effects(app) <= 0) return;
        String asset = "art/" + path.trim();
        if ("\"music\"".equals(keys.getProperty(key + ".type"))) {
            playJingle(asset);
            return;
        }
        Integer id = ids.get(asset);
        if (id == null) {
            try (AssetFileDescriptor fd = app.getAssets().openFd(asset)) {
                id = pool.load(fd, 1);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Can not load " + asset, e);
                return;
            }
            ids.put(asset, id);
        }
        if (ready.contains(id)) {
            playLoaded(id);
        } else {
            playWhenReady.add(id);
        }
    }

    private void playLoaded(int id) {
        float v = Settings.effects(app) / 100f;
        pool.play(id, v, v, 1, 0, 1f);
    }

    /** A longer piece: the music is turned down while it plays. */
    private void playJingle(String asset) {
        if (jingle != null) {
            jingle.release();
            jingle = null;
        }
        MediaPlayer mp = player(asset);
        if (mp == null) return;
        float v = Settings.effects(app) / 100f;
        mp.setVolume(v, v);
        mp.setOnCompletionListener(done -> {
                synchronized (this) {
                    done.release();
                    if (jingle == done) jingle = null;
                    updateMusic();
                }
            });
        jingle = mp;
        updateMusic();
        mp.start();
    }
}
