package ru.dmitrybochkov.movingcar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import tt.euclidyaw3d.dubins.DubinsCurve;

/**
 * Created by Dmitry Bochkov on 14.11.2019.
 */

/***
 * Используем Dubins path http://planning.cs.uiuc.edu/node821.html , спасибо этим ребятам
 * https://github.com/mcapino/trajectorytools
 * Для случаев, когда, условно говоря, пункт назначения слишком близко и не по направлению движения,
 * подойдет Reeds-Shepp Car http://planning.cs.uiuc.edu/node822.html
 */

public class CarView extends View {

    private static final int CAR_WIDTH = 60;
    private static final int CAR_LENGTH = 129;

    private int radius = 200;
    private int speed = 520;

    private tt.euclidyaw3d.Point carPosition;

    DubinsCurve dc;
    private tt.euclidyaw3d.Point[] path;

    private GestureDetector mTapDetector;

    public CarView(Context context) {
        super(context);
        init();
    }

    public CarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
            mTapDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent event) {
                    calculatePath(new Point(Math.round(event.getX()), Math.round(event.getY())));
                    startMoving();
                    return super.onSingleTapConfirmed(event);
                }
            });
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (carPosition == null) {
            carPosition = new tt.euclidyaw3d.Point(
                    getMeasuredWidth() / 2,
                    getMeasuredHeight() / 2,
                    -Math.PI / 2);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.rotate((float)(carPosition.getYaw() * 180 / Math.PI), (float)carPosition.x, (float)carPosition.y);
        Drawable d = ContextCompat.getDrawable(getContext(), R.drawable.car);
        d.setBounds(calculateCarRect((int)Math.round(carPosition.x), (int)Math.round(carPosition.y)));
        d.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mTapDetector.onTouchEvent(event);
        return true;
    }

    private void startMoving() {
        CarAnimation carAnimation = new CarAnimation(speed, dc.getLength());
        startAnimation(carAnimation);
    }

    private void calculatePath(Point touchPoint) {
//        Направление (heading) конечной точки вычисляется как направление от исходной точки к конечной,
//        но это, конечно, в итоге не самый оптимальный маршрут (хотя иногда и да).
//        Оптимальное будет по касательной к одной из возможных окружностей r = radius, проведенных
//        через исходную точку.
        double endYaw = Math.atan2(touchPoint.y - carPosition.y, touchPoint.x - carPosition.x);
        tt.euclidyaw3d.Point end = new tt.euclidyaw3d.Point(touchPoint.x, touchPoint.y, endYaw);
        dc = new DubinsCurve(carPosition, end, radius, false);
        path = dc.interpolateUniformBy(10); //Вот это в перспективе оптимизировать бы в согласии с applyTransformation
    }

    private Rect calculateCarRect(int centerX, int centerY) {
        int left = centerX - (CAR_LENGTH / 2);
        int top = centerY - (CAR_WIDTH / 2);
        int right = centerX + (CAR_LENGTH / 2);
        int bottom = centerY + (CAR_WIDTH / 2);
        return new Rect(left, top, right, bottom);
    }

    class CarAnimation extends Animation {

        CarAnimation(float speed, double distance) {
            setInterpolator(new AccelerateDecelerateInterpolator());
            setDuration(Math.round(distance / speed) * 1000); //Ускорение учтем в другой раз
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            int i = Math.round(path.length * interpolatedTime);
            if (i < path.length) {
                carPosition.x = path[i].x;
                carPosition.y = path[i].y;
                carPosition.z = path[i].z;
                invalidate();
            }
        }

    }

}
