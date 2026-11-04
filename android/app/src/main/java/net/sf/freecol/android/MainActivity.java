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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/**
 * Phase 1 test screen: runs an all-AI game with the shared engine and
 * shows its progress.  This is a proof that the engine works on the
 * phone, not the real game user interface.
 */
public final class MainActivity extends Activity {

    private static final int TURNS = 50;

    private TextView output;
    private ScrollView scroll;
    private Button runButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad * 2, pad, pad);

        runButton = new Button(this);
        runButton.setText("Run " + TURNS + " AI turns");
        runButton.setOnClickListener(v -> startRun());
        layout.addView(runButton);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setText("FreeCol engine test. Tap the button to let the AI "
            + "players play a game on this phone.\n");
        scroll = new ScrollView(this);
        scroll.addView(output);
        layout.addView(scroll, new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(layout);
    }

    private void startRun() {
        runButton.setEnabled(false);
        new Thread(() -> {
            try {
                // StAX implementation, in case service lookup fails.
                System.setProperty("javax.xml.stream.XMLInputFactory",
                    "com.ctc.wstx.stax.WstxInputFactory");
                System.setProperty("javax.xml.stream.XMLOutputFactory",
                    "com.ctc.wstx.stax.WstxOutputFactory");
                System.setProperty("javax.xml.stream.XMLEventFactory",
                    "com.ctc.wstx.stax.WstxEventFactory");

                File data = new File(getFilesDir(), "data");
                log("Unpacking game data...");
                copyAssets(getAssets(), "data", data);
                AutoPlay.Result r = AutoPlay.run(data, TURNS,
                    20 * 60 * 1000L, this::log);
                log(r.toString());
            } catch (Throwable t) {
                log("FAILED: " + t);
            }
            runOnUiThread(() -> runButton.setEnabled(true));
        }, "AutoPlay").start();
    }

    private void log(String line) {
        runOnUiThread(() -> {
            output.append(line + "\n");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    /**
     * Copy an asset directory tree to a real directory, since the
     * engine reads its data from files.
     */
    private static void copyAssets(AssetManager am, String path, File dest)
        throws IOException {
        String[] children = am.list(path);
        if (children == null || children.length == 0) {
            dest.getParentFile().mkdirs();
            try (InputStream in = am.open(path);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        dest.mkdirs();
        for (String child : children) {
            copyAssets(am, path + "/" + child, new File(dest, child));
        }
    }
}
