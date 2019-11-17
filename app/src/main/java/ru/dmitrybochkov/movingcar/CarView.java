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

import org.apache.commons.lang3.ArrayUtils;

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

    private boolean showDestination = false;

    Drawable carDrawable;
    private tt.euclidyaw3d.Point carPosition;
    private CarDestination carDestination;

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
        carDrawable = ContextCompat.getDrawable(getContext(), R.drawable.car);
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

    public boolean isShowDestination() {
        return showDestination;
    }

    public void setShowDestination(boolean showDestination) {
        this.showDestination = showDestination;
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
        carDrawable.setBounds(calculateCarRect((int)Math.round(carPosition.x), (int)Math.round(carPosition.y)));
        carDrawable.draw(canvas);
        canvas.restore();

        if (showDestination && carDestination != null && !carPosition.equals(carDestination.point)) {
            canvas.save();
            canvas.rotate(carDestination.rotation, (float) carDestination.point.x, (float) carDestination.point.y);
            carDrawable.setBounds(carDestination.bounds);
            carDrawable.setAlpha(88);
            carDrawable.draw(canvas);
            carDrawable.setAlpha(255);
            canvas.restore();
        }
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

        //Посчитаем сразу все параметры необходимые для отрисовки, чтобы не пересчитывать
        carDestination = new CarDestination(
                new tt.euclidyaw3d.Point(touchPoint.x, touchPoint.y, endYaw),
                (float) (endYaw * 180 / Math.PI),
                calculateCarRect(touchPoint.x, touchPoint.y));
        carDestination.point = new tt.euclidyaw3d.Point(touchPoint.x, touchPoint.y, endYaw);
        dc = new DubinsCurve(carPosition, carDestination.point, radius, false);
        path = dc.interpolateUniformBy(10); //Вот это в перспективе оптимизировать бы в согласии с applyTransformation
        path = ArrayUtils.add(path, carDestination.point); //Алгоритм теряет последнюю точку, потому добавим её вручную для точности
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
            int i = Math.round((path.length - 1) * interpolatedTime);
            if (i < path.length) {
                carPosition.x = path[i].x;
                carPosition.y = path[i].y;
                carPosition.z = path[i].z;
                invalidate();
            }
            super.applyTransformation(interpolatedTime, t);
        }

    }

    class CarDestination {
        tt.euclidyaw3d.Point point;
        float rotation;
        Rect bounds;

        public CarDestination(tt.euclidyaw3d.Point point, float rotation, Rect bounds) {
            this.point = point;
            this.rotation = rotation;
            this.bounds = bounds;
        }
    }

}
