package ec.yttvpremium.access;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Logo de YTTVPremium (mismo dibujo que YTPremium): círculo rojo, play y 3 barras. */
final class LogoView extends View {
    private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path play = new Path();
    private final RectF rect = new RectF();

    LogoView(Context context) {
        super(context);
        red.setColor(0xFFD91E2E);
        white.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight()) / 1024f;
        canvas.save();
        canvas.translate((getWidth() - 1024 * s) / 2f, (getHeight() - 1024 * s) / 2f);
        canvas.scale(s, s);
        canvas.drawCircle(512, 512, 480, red);
        play.reset();
        play.moveTo(262, 330);
        play.quadTo(240, 316, 240, 344);
        play.lineTo(240, 680);
        play.quadTo(240, 708, 262, 694);
        play.lineTo(540, 530);
        play.quadTo(564, 512, 540, 494);
        play.close();
        canvas.drawPath(play, white);
        bar(canvas, 600, 430, 164);
        bar(canvas, 672, 350, 324);
        bar(canvas, 744, 400, 224);
        canvas.restore();
    }

    private void bar(Canvas canvas, float x, float y, float h) {
        rect.set(x, y, x + 44, y + h);
        canvas.drawRoundRect(rect, 22, 22, white);
    }
}
