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

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/**
 * The start screen: continue the saved game, begin a new one, or run
 * the Phase 1 engine test
 * (an all-AI game with no map, showing its progress as text).
 */
public final class MainActivity extends Activity {

    private static final int TURNS = 50;

    private TextView output;
    private ScrollView scroll;
    private Button runButton;
    private Button continueButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No title bar: Android 15+ draws apps edge-to-edge, and the
        // title bar would otherwise cover the top of the screen.
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        // Keep clear of the status bar, navigation bar and any camera
        // cutout, which edge-to-edge drawing would otherwise overlap.
        layout.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad + bars.top,
                         pad + bars.right, pad + bars.bottom);
            return WindowInsets.CONSUMED;
        });

        continueButton = new Button(this);
        continueButton.setAllCaps(false);
        continueButton.setOnClickListener(v -> startActivity(
            new android.content.Intent(this, GameActivity.class)
                .putExtra(GameActivity.EXTRA_CONTINUE, true)));
        layout.addView(continueButton);

        Button newGame = new Button(this);
        newGame.setText("New game (you against the AI)");
        newGame.setAllCaps(false);
        newGame.setOnClickListener(v -> {
                if (GameSession.describeSave(GameActivity.saveFile(this)) == null) {
                    startNewGame();
                    return;
                }
                new android.app.AlertDialog.Builder(this)
                    .setMessage("A new game will replace your saved game "
                        + "when its first turn starts.")
                    .setPositiveButton("New game", (d, w) -> startNewGame())
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        layout.addView(newGame);

        runButton = new Button(this);
        runButton.setAllCaps(false);
        runButton.setText("Engine test: run " + TURNS + " AI turns");
        runButton.setOnClickListener(v -> startRun());
        layout.addView(runButton);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setText("The engine test lets the AI players play a game "
            + "on this phone, without the map.\n");
        scroll = new ScrollView(this);
        scroll.addView(output);
        layout.addView(scroll, new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String about = GameSession.describeSave(GameActivity.saveFile(this));
        continueButton.setVisibility((about == null) ? android.view.View.GONE
                                     : android.view.View.VISIBLE);
        if (about != null) continueButton.setText("Continue: " + about);
    }

    private void startNewGame() {
        startActivity(new android.content.Intent(this, GameActivity.class));
    }

    private void startRun() {
        runButton.setEnabled(false);
        new Thread(() -> {
            try {
                File data = new File(getFilesDir(), "data");
                log("Unpacking game data...");
                Assets.copy(getAssets(), "data", data);
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
}
