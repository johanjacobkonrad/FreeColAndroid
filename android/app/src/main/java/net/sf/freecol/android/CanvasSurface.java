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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.Surface;


/** A map {@link Surface} that draws on an Android canvas. */
final class CanvasSurface implements Surface {
    Canvas canvas;
    private final RectF dst = new RectF();
    private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint faded = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    CanvasSurface() {
        ColorMatrix m = new ColorMatrix();
        m.setScale(0.62f, 0.62f, 0.62f, 1f);
        faded.setColorFilter(new ColorMatrixColorFilter(m));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    private final android.graphics.Path path = new android.graphics.Path();

    @Override
    public void drawCurve(float x1, float y1, float cx, float cy, float x2, float y2,
                          float width, int argb) {
        line.setColor(argb);
        line.setStrokeWidth(width);
        line.setStyle(Paint.Style.STROKE);
        path.reset();
        path.moveTo(x1, y1);
        path.quadTo(cx, cy, x2, y2);
        canvas.drawPath(path, line);
        line.setStyle(Paint.Style.FILL);
    }

    @Override
    public void drawLine(float x1, float y1, float x2, float y2, float width,
                         int argb) {
        line.setColor(argb);
        line.setStrokeWidth(width);
        canvas.drawLine(x1, y1, x2, y2, line);
    }

    @Override
    public void drawImage(Art.Img img, float x, float y, float w, float h,
                          boolean fade) {
        dst.set(x, y, x + w, y + h);
        canvas.drawBitmap((Bitmap)img.handle, null, dst, fade ? faded : plain);
    }

    @Override
    public void drawImageFlipped(Art.Img img, float x, float y, float w, float h) {
        canvas.save();
        canvas.scale(-1f, 1f, x + w / 2f, y + h / 2f);
        dst.set(x, y, x + w, y + h);
        canvas.drawBitmap((Bitmap)img.handle, null, dst, plain);
        canvas.restore();
    }

    @Override
    public void fillRect(float x, float y, float w, float h, int argb) {
        fill.setColor(argb);
        canvas.drawRect(x, y, x + w, y + h, fill);
    }

    @Override
    public void drawText(String s, float cx, float baseline, float size,
                         int argb) {
        text.setColor(argb);
        text.setTextSize(size);
        canvas.drawText(s, cx, baseline, text);
    }
}
