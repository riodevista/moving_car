package ru.dmitrybochkov.movingcar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import tt.euclidyaw3d.Point;
import tt.euclidyaw3d.dubins.DubinsCurve;

/***
 * Created by Dmitry Bochkov on 14.11.2019.
 * Используем Dubins path http://planning.cs.uiuc.edu/node821.html , спасибо этим ребятам
 * https://github.com/mcapino/trajectorytools
 * Для случаев, когда, условно говоря, пункт назначения слишком близко и не по направлению движения,
 * подойдет Reeds-Shep Car http://planning.cs.uiuc.edu/node822.html
 */

public class CarView extends View {

    private int radius = 200;
    private boolean showDestination = false;

    private Car car;
    private Car carDestination;
    private OutOfScreenMarkers outOfScreenMarkers;

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
        car = new Car(ContextCompat.getDrawable(getContext(), R.drawable.car));
        carDestination = new Car(ContextCompat.getDrawable(getContext(), R.drawable.car));
        outOfScreenMarkers = new OutOfScreenMarkers(Color.parseColor("#F4B400"));

        mTapDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent event) {
                    moveTo(event.getX(), event.getY());
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

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (car.isPositionUndefined()) {
            car.setPosition(new Point(
                    getMeasuredWidth() / 2,
                    getMeasuredHeight() / 2,
                    -Math.PI / 2));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        car.draw(canvas, 255);
        if (showDestination && !car.isPositionSameTo(carDestination)) {
            carDestination.draw(canvas, 88);
        }
        outOfScreenMarkers.draw(canvas);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mTapDetector.onTouchEvent(event);
        return true;
    }

    private void moveTo(float touchX, float touchY) {
//        Направление (heading) конечной точки вычисляется как направление от исходной точки к конечной,
//        но это, конечно, в итоге не самый оптимальный маршрут (хотя иногда и да).
//        Оптимальное будет по касательной к одной из возможных окружностей r = radius, проведенных
//        через исходную точку.
        double endYaw = Math.atan2(touchY - car.getPosition().y, touchX - car.getPosition().x);
        carDestination.setPosition(new Point(touchX, touchY, endYaw));
        DubinsCurve dc = new DubinsCurve(car.getPosition(), carDestination.getPosition(), radius, false);
        Point[] path = dc.interpolateUniformBy(10); //Вот это в перспективе оптимизировать бы в согласии с applyTransformation
        path = ArrayUtils.add(path, carDestination.getPosition()); //Алгоритм теряет последнюю точку, потому добавим её вручную для точности

        CarAnimation carAnimation = new CarAnimation(path, Car.SPEED, dc.getLength());
        startAnimation(carAnimation);
    }


    private class CarAnimation extends Animation {
        private Point[] path;

        CarAnimation(Point[] path, float speed, double distance) {
            this.path = path;
            setInterpolator(new AccelerateDecelerateInterpolator());
            setDuration(Math.round(distance / speed) * 1000); //Ускорение учтем в другой раз
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            int i = Math.round((path.length - 1) * interpolatedTime);
            if (i < path.length) {
                car.setPosition(path[i]);
                outOfScreenMarkers.setData(car.getPosition(), car.getBounds());
                invalidate();
            }
            super.applyTransformation(interpolatedTime, t);
        }
    }

    private class Car {
        private static final int CAR_WIDTH = 60;
        private static final int CAR_LENGTH = 129;
        static final int SPEED = 520;

        private Point position;
        private float canvasRotation;
        private Rect bounds;

        Drawable carDrawable;

        Car(Drawable drawable) {
            carDrawable = drawable;
        }

        void setPosition(Point position) {
            this.position = position;
            canvasRotation = (float)(position.getYaw() * 180 / Math.PI);
            bounds = calculateBounds((int)Math.round(position.x), (int)Math.round(position.y));
        }

        private Rect calculateBounds(int centerX, int centerY) {
            int left = centerX - (CAR_LENGTH / 2);
            int top = centerY - (CAR_WIDTH / 2);
            int right = centerX + (CAR_LENGTH / 2);
            int bottom = centerY + (CAR_WIDTH / 2);
            return new Rect(left, top, right, bottom);
        }

        void draw(@NonNull Canvas canvas, int alpha) {
            canvas.save();
            canvas.rotate(canvasRotation, (float)position.x, (float)position.y);
            carDrawable.setBounds(bounds);
            carDrawable.setAlpha(alpha);
            carDrawable.draw(canvas);
            canvas.restore();
        }

        boolean isPositionSameTo(Car car) {
            return this.position.equals(car.getPosition());
        }

        boolean isPositionUndefined() {
            return position == null;
        }

        Point getPosition() {
            return position;
        }

        Rect getBounds() {
            return bounds;
        }
    }


    private class OutOfScreenMarkers {
        private Paint paint = new Paint();

        private Point horizontalMarker;
        private Point verticalMarker;

        OutOfScreenMarkers(@ColorInt int color) {
            paint.setColor(color);
            paint.setStrokeWidth(20f);
        }

        void setData(Point carPosition, Rect carBounds) {
            if (carBounds.left > getMeasuredWidth()) {
                float y = Math.min(Math.max(0, (float)carPosition.y), getMeasuredHeight());
                horizontalMarker = new Point(getMeasuredWidth(), y, 0);
            } else if (carBounds.right < 0) {
                float y = Math.min(Math.max(0, (float)carPosition.y), getMeasuredHeight());
                horizontalMarker = new Point(0, y, 0);
            } else
                horizontalMarker = null;

            if (carBounds.top > getMeasuredHeight()) {
                float x = Math.min(Math.max(0, (float)carPosition.x), getMeasuredWidth());
                verticalMarker = new Point(x, getMeasuredHeight(), 0);
            } else if (carBounds.bottom < 0) {
                float x = Math.min(Math.max(0, (float)carPosition.x), getMeasuredWidth());
                verticalMarker = new Point(x, 0, 0);
            } else
                verticalMarker = null;
        }

        void draw(@NonNull Canvas canvas) {
            if (horizontalMarker != null)
                canvas.drawPoint((float)horizontalMarker.x, (float)horizontalMarker.y, paint);
            if (verticalMarker != null)
                canvas.drawPoint((float)verticalMarker.x, (float)verticalMarker.y, paint);
        }
    }


}
