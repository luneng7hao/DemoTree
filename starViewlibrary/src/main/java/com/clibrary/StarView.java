package com.clibrary;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


/**
 * 自定义的五角星view
 * 可设置显示数量，以及实心VIew与空心View的显示设置
 */
public class StarView extends View {

    //实心图片
    private Bitmap mSolidBitmap;
    //空心图片
    private Bitmap mHollowBitmap;
    //最大的数量
    private int starMaxNumber;
    //实心最小数量
    private int starMinNumber;
    private float starRating;
    private Paint paint;
    private int mSpaceWidth;//星星间隔
    private int mStarWidth;//星星宽度
    private int mStarHeight;//星星高度
    private boolean isIndicator;//是否是一个指示器（默认false，不可设置实心与空心图标的显示）

    public StarView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StarView, defStyleAttr, 0);
        mSpaceWidth = a.getDimensionPixelSize(R.styleable.StarView_space_width, 0);
        mStarWidth = a.getDimensionPixelSize(R.styleable.StarView_star_width, 0);
        mStarHeight = a.getDimensionPixelSize(R.styleable.StarView_star_height, 0);

        //由于作图时，是按照图标的一半进行绘制，以下三个参数的数量需乘以2
        starMaxNumber = a.getInt(R.styleable.StarView_star_max, 0) * 2;
        starMinNumber = a.getInt(R.styleable.StarView_star_min, 0) * 2;
        starRating = a.getFloat(R.styleable.StarView_star_rating, 0) * 2;

        mSolidBitmap = getZoomBitmap(BitmapFactory.decodeResource(context.getResources(), a.getResourceId(R.styleable.StarView_star_solid, 0)));
        mHollowBitmap = getZoomBitmap(BitmapFactory.decodeResource(context.getResources(), a.getResourceId(R.styleable.StarView_star_hollow, 0)));
        isIndicator = a.getBoolean(R.styleable.StarView_star_isIndicator, false);
        a.recycle();
    }

    /**
     * 获取缩放的图片
     * @param bitmap
     * @return
     */
    private Bitmap getZoomBitmap(Bitmap bitmap) {
        if (mStarWidth == 0 || mStarHeight == 0) {
            return bitmap;
        }
        // 获得图片的宽高
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 设置想要的大小
        int newWidth = mStarWidth;
        int newHeight = mStarHeight;
        // 计算缩放比例
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // 取得想要缩放的matrix参数
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        // 得到新的图片
        Bitmap newbm = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        return newbm;
    }
    @Override
    protected void onDraw(Canvas canvas) {
        if (mHollowBitmap == null || mSolidBitmap == null) {
            return;
        }

        //绘制实心进度
        int solidStarNum = (int) starRating;
        //绘制实心的起点位置
        int solidStartPoint = 0;

        //设置实心图标显示一半
        // 指定图片绘制区域(左半边)
        Rect srcleft = new Rect(0, 0, mSolidBitmap.getWidth() / 2, mSolidBitmap.getHeight());
        // 指定图片绘制区域(右半边)
        Rect srcright = new Rect(mSolidBitmap.getWidth() / 2, 0, mSolidBitmap.getWidth(), mSolidBitmap.getHeight());

        int iconLeft=0;
        for (int i = 1; i <= solidStarNum; i++) {

            // 指定图片在屏幕上显示的区域
            Rect dst = new Rect(iconLeft, 0, iconLeft+mSolidBitmap.getWidth()/2, mSolidBitmap.getHeight());
            // 绘制图片
            if(i%2==1) {
                canvas.drawBitmap(mSolidBitmap, srcleft, dst, null);
                iconLeft+=mSolidBitmap.getWidth()/2;
            }else{
                canvas.drawBitmap(mSolidBitmap, srcright, dst, null);
                iconLeft +=mSolidBitmap.getWidth()/2+mSpaceWidth;
            }
            solidStartPoint=iconLeft;

        }
        //虚心开始位置
        int hollowStartPoint = solidStartPoint;
        //多出的实心部分起点
        int extraSolidStarPoint = hollowStartPoint;
        //虚心数量
        int hollowStarNum =starMaxNumber - solidStarNum;
        for (int j = 1; j <= hollowStarNum; j++) {

            // 指定图片在屏幕上显示的区域
            Rect dst = new Rect(hollowStartPoint, 0, hollowStartPoint + mHollowBitmap.getWidth() / 2, mHollowBitmap.getHeight());
            // 绘制图片
            //根据取余运算，判断绘制空心图的哪一部分
            if(hollowStarNum%2==0) {
                if (j % 2 == 1) {
                    canvas.drawBitmap(mHollowBitmap, srcleft, dst, null);
                    hollowStartPoint += mHollowBitmap.getWidth() / 2;
                } else {
                    canvas.drawBitmap(mHollowBitmap, srcright, dst, null);
                    hollowStartPoint += mHollowBitmap.getWidth() / 2 + mSpaceWidth;
                }
            }else{
                if (j % 2 == 1) {
                    canvas.drawBitmap(mHollowBitmap, srcright, dst, null);
                    hollowStartPoint += mHollowBitmap.getWidth() / 2 + mSpaceWidth;

                } else {

                    canvas.drawBitmap(mHollowBitmap, srcleft, dst, null);
                    hollowStartPoint += mHollowBitmap.getWidth() / 2;
                }
            }
        }
        //多出的实心长度
        int extraSolidLength = (int) ((starRating - solidStarNum) * mHollowBitmap.getWidth());
        Rect rectSrc = new Rect(0, 0, extraSolidLength, mHollowBitmap.getHeight());
        Rect dstF = new Rect(extraSolidStarPoint, 0, extraSolidStarPoint + extraSolidLength, mHollowBitmap.getHeight());
        canvas.drawBitmap(mSolidBitmap, rectSrc, dstF, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isIndicator) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    float TotalWidth = starMaxNumber *( mStarWidth+mSpaceWidth);
                    if (event.getX()>(starMinNumber-1)*mStarWidth/2&&event.getX() <= TotalWidth) {
                        float newStarRating = (int) event.getX() / ((mStarWidth+mSpaceWidth)/2 ) +1;
                        setStarRating(newStarRating);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_CANCEL:
                    break;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * 设置星星的进度
     *
     * @param starRating
     */
    public void setStarRating(float starRating) {
        this.starRating = starRating;
        invalidate();
    }

    public float getStarRating() {
        return starRating;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //判断是横向还是纵向，测量长度
        setMeasuredDimension(measureLong(widthMeasureSpec), measureShort(heightMeasureSpec));
    }

    private int measureLong(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if ((specMode == MeasureSpec.EXACTLY)) {
            result = specSize;
        } else {
            result = (int) (getPaddingLeft() + getPaddingRight() + (mSpaceWidth + mStarWidth) * (starMaxNumber));
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    private int measureShort(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            result = (int) (mStarHeight + getPaddingTop() + getPaddingBottom());
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    public int getStarMaxNumber() {
        return starMaxNumber;
    }

    public void setStarMaxNumber(int starMaxNumber) {
        this.starMaxNumber = starMaxNumber;
        //利用invalidate()；刷新界面
        invalidate();
    }

    public boolean isIndicator() {
        return isIndicator;
    }

    public void setIsIndicator(boolean isIndicator) {
        this.isIndicator = isIndicator;
    }
}
