package com.meancoder.livetextfinder;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.Locale;
import java.util.Random;

/** Draw the detected object info in preview. */
public class ObjectGraphic extends GraphicOverlay.Graphic {

    public static float STROKE_WIDTH = 4.0f;
    private static final int NUM_COLORS = 10;
    private Random rand = new Random();
    public static int colorID=1;
    public static final int[] COLORS =
            new int[] {
                    // {Text color, background color}
                    Color.RED,
                    Color.GREEN,
                    Color.argb(255,50,255,100),
                    Color.argb(255,50,150,50),
                    Color.BLUE,
                    Color.argb(255,50,50,255),
                    Color.YELLOW,
                    Color.MAGENTA,
                    Color.BLACK,
                    Color.argb(255,100,100,100),
                    Color.argb(255,200,100,100),
                    Color.argb(255,100,200,200),
            };
    private static final String LABEL_FORMAT = "%.2f%% confidence (index: %d)";

    private final Paint[] boxPaints;
    private Rect rect;
    public ObjectGraphic(GraphicOverlay overlay, Rect rect) {
        super(overlay);
        this.rect = rect;
        int numColors = COLORS.length;
        boxPaints = new Paint[numColors];
        for (int i = 0; i < numColors; i++) {
            boxPaints[i] = new Paint();
            boxPaints[i].setColor(COLORS[i]);
            boxPaints[i].setStyle(Paint.Style.STROKE);
            boxPaints[i].setStrokeWidth(STROKE_WIDTH);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        // Decide color based on object tracking ID
        // Draws the bounding box.
        // If the image is flipped, the left will be translated to right, and the right to left.
        float x0 = translateX(rect.left);
        float x1 = translateX(rect.right);
        rect.left = (int) Math.min(x0, x1);
        rect.right = (int) Math.max(x0, x1);
        rect.top = (int) translateY(rect.top);
        rect.bottom = (int) translateY(rect.bottom);
        canvas.drawRect(rect, boxPaints[colorID]);

    }
}