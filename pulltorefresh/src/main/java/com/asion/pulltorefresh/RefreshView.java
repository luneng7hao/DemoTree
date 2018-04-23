package com.asion.pulltorefresh;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.asion.asionpulltorefresh.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class RefreshView extends LinearLayout implements View.OnTouchListener {

    private static final String TAG = RefreshView.class.getSimpleName();

    public enum PULL_STATUS {
        STATUS_PULL_TO_REFRESH(0), // 下拉状态
        STATUS_RELEASE_TO_REFRESH(1), // 释放立即刷新状态
        STATUS_REFRESHING(2), // 正在刷新状态
        STATUS_REFRESH_FINISHED(3); // 刷新完成或未刷新状态

        private int status; // 状态

        PULL_STATUS(int value) {
            this.status = value;
        }

        public int getValue() {
            return this.status;
        }
    }

    // 下拉头部回滚的速度
    public static final int SCROLL_SPEED = -20;
    // 一分钟的毫秒值，用于判断上次的更新时间
    public static final long ONE_MINUTE = 60 * 1000;
    // 一小时的毫秒值，用于判断上次的更新时间
    public static final long ONE_HOUR = 60 * ONE_MINUTE;
    // 一天的毫秒值，用于判断上次的更新时间
    public static final long ONE_DAY = 24 * ONE_HOUR;
    // 一月的毫秒值，用于判断上次的更新时间
    public static final long ONE_MONTH = 30 * ONE_DAY;
    // 一年的毫秒值，用于判断上次的更新时间
    public static final long ONE_YEAR = 12 * ONE_MONTH;
    // 上次更新时间的字符串常量，用于作为 SharedPreferences 的键值
    private static final String UPDATED_AT = "updated_at";
    /** 时间日期格式化到年月日时分秒. */
    public static final String dateFormatYMDHMS = "yyyy-MM-dd HH:mm:ss";
    /** 时分. */
    public static final String dateFormatHM = "HH:mm";

    // 下拉刷新的回调接口
    private PullToRefreshListener mListener;

    private SharedPreferences preferences; // 用于存储上次更新时间
    private View header; // 下拉头的View
    private ListView listView; // 需要去下拉刷新的ListView

    private ProgressBar progressBar; // 刷新时显示的进度条
    private ImageView arrow; // 指示下拉和释放的箭头
    private TextView description; // 指示下拉和释放的文字描述
    private TextView updateAt; // 上次更新时间的文字描述

    private MarginLayoutParams headerLayoutParams; // 下拉头的布局参数
    private long lastUpdateTime=-1; // 上次更新时间的毫秒值

    // 为了防止不同界面的下拉刷新在上次更新时间上互相有冲突，使用id来做区分
    private int mId = -1;

    private int hideHeaderHeight; // 下拉头的高度

    /**
     * 当前处理什么状态，可选值有 STATUS_PULL_TO_REFRESH, STATUS_RELEASE_TO_REFRESH, STATUS_REFRESHING 和 STATUS_REFRESH_FINISHED
     */
    private PULL_STATUS currentStatus = PULL_STATUS.STATUS_REFRESH_FINISHED;

    // 记录上一次的状态是什么，避免进行重复操作
    private PULL_STATUS lastStatus = currentStatus;

    private float yDown; // 手指按下时的屏幕纵坐标

    private int touchSlop; // 在被判定为滚动之前用户手指可以移动的最大值。

    private boolean loadOnce; // 是否已加载过一次layout，这里onLayout中的初始化只需加载一次

    private boolean ableToPull; // 当前是否可以下拉，只有ListView滚动到头的时候才允许下拉

    /**
     * 下拉刷新控件的构造函数，会在运行时动态添加一个下拉头的布局
     */
    public RefreshView(Context context, AttributeSet attrs) {
        super(context, attrs);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        header = LayoutInflater.from(context).inflate(R.layout.pull_to_refresh, null, true);
        progressBar = (ProgressBar) header.findViewById(R.id.progress_bar);
        arrow = (ImageView) header.findViewById(R.id.arrow);
        description = (TextView) header.findViewById(R.id.description);
        updateAt = (TextView) header.findViewById(R.id.updated_at);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        refreshUpdatedAtValue();
        setOrientation(VERTICAL);
        addView(header, 0);

        //Log.d(TAG, "RefreshView Constructor() getChildAt(0): " + getChildAt(0));
        //Log.d(TAG, "RefreshView Constructor() getChildAt(0): " + getChildAt(1));

//        listView = (ListView) getChildAt(1);
//        listView.setOnTouchListener(this);
    }

    /**
     * 进行一些关键性的初始化操作，比如：将下拉头向上偏移进行隐藏，给 ListView 注册 touch 事件
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !loadOnce) {
            hideHeaderHeight = -header.getHeight();

            headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
            headerLayoutParams.topMargin = hideHeaderHeight;
            listView = (ListView) getChildAt(1);
            //Log.d(TAG, "onLayout() getChildAt(0): " + getChildAt(0));
            //Log.d(TAG, "onLayout() listView: " + listView);
            listView.setOnTouchListener(this);
            loadOnce = true;
        }
    }

    /**
     * 当 ListView 被触摸时调用，其中处理了各种下拉刷新的具体逻辑
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        setCanAbleToPull(event); // 判断是否可以下拉
        if (ableToPull) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    // 获取移动中的 Y 轴的位置
                    float yMove = event.getRawY();
                    // 获取从按下到移动过程中移动的距离
                    int distance = (int) (yMove - yDown);

                    // 如果手指是上滑状态，并且下拉头是完全隐藏的，就屏蔽下拉事件
                    if (distance <= 0 && headerLayoutParams.topMargin <= hideHeaderHeight) {
                        return false;
                    }
                    if (distance < touchSlop) {
                        return false;
                    }
                    // 判断是否已经在刷新状态
                    if (currentStatus != PULL_STATUS.STATUS_REFRESHING) {
                        // 判断设置的 topMargin 是否 > 0, 默认初始设置为 -header.getHeight()
                        if (headerLayoutParams.topMargin > 0) {
                            currentStatus = PULL_STATUS.STATUS_RELEASE_TO_REFRESH;
                        } else {
                            // 否则状态为下拉中的状态
                            currentStatus = PULL_STATUS.STATUS_PULL_TO_REFRESH;
                        }
                        // 通过偏移下拉头的 topMargin 值，来实现下拉效果
                        headerLayoutParams.topMargin = (distance / 2) + hideHeaderHeight;
                        header.setLayoutParams(headerLayoutParams);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatus == PULL_STATUS.STATUS_RELEASE_TO_REFRESH) {
                        // 松手时如果是释放立即刷新状态，就去调用正在刷新的任务
                        new RefreshingTask().execute();
                    } else if (currentStatus == PULL_STATUS.STATUS_PULL_TO_REFRESH) {
                        // 松手时如果是下拉状态，就去调用隐藏下拉头的任务
                        new HideHeaderTask().execute();
                    }
                    break;
            }
            // 时刻记得更新下拉头中的信息
            if (currentStatus == PULL_STATUS.STATUS_PULL_TO_REFRESH
                    || currentStatus == PULL_STATUS.STATUS_RELEASE_TO_REFRESH) {
                updateHeaderView();
                // 当前正处于下拉或释放状态，要让 ListView 失去焦点，否则被点击的那一项会一直处于选中状态
                listView.setPressed(false);
                listView.setFocusable(false);
                listView.setFocusableInTouchMode(false);
                lastStatus = currentStatus;
                // 当前正处于下拉或释放状态，通过返回 true 屏蔽掉 ListView 的滚动事件
                return true;
            }
        }
        return false;
    }

    /**
     * 给下拉刷新控件注册一个监听器
     *
     * @param listener 监听器的实现
     * @param id       为了防止不同界面的下拉刷新在上次更新时间上互相有冲突，不同界面在注册下拉刷新监听器时一定要传入不同的 id
     */
    public void setOnRefreshListener(PullToRefreshListener listener, int id) {
        mListener = listener;
        mId = id;
    }

    /**
     * 当所有的刷新逻辑完成后，记录调用一下，否则你的 ListView 将一直处于正在刷新状态
     */
    public void finishRefreshing() {
        currentStatus = PULL_STATUS.STATUS_REFRESH_FINISHED;
        preferences.edit().putLong(UPDATED_AT + mId, System.currentTimeMillis()).commit();
        new HideHeaderTask().execute();
    }

    /**
     * 根据当前 ListView 的滚动状态来设定 {@link #ableToPull}
     * 的值，每次都需要在 onTouch 中第一个执行，这样可以判断出当前应该是滚动 ListView，还是应该进行下拉
     */
    private void setCanAbleToPull(MotionEvent event) {
        View firstChild = listView.getChildAt(0);
        if (firstChild != null) {
            // 获取 ListView 中第一个Item的位置
            int firstVisiblePos = listView.getFirstVisiblePosition();
            // 判断第一个子控件的 Top 是否和第一个 Item 位置相等
            if (firstVisiblePos == 0 && firstChild.getTop() == 0) {
                if (!ableToPull) {
                    // getRawY() 获得的是相对屏幕 Y 方向的位置
                    yDown = event.getRawY();
                }
                // 如果首个元素的上边缘，距离父布局值为 0，就说明 ListView 滚动到了最顶部，此时应该允许下拉刷新
                ableToPull = true;
            } else {
                if (headerLayoutParams.topMargin != hideHeaderHeight) {
                    headerLayoutParams.topMargin = hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                }
                ableToPull = false;
            }
        } else {
            // 如果 ListView 中没有元素，也应该允许下拉刷新
            ableToPull = true;
        }
    }

    /**
     * 更新下拉头中的信息
     */
    private void updateHeaderView() {
        if (lastStatus != currentStatus) {
            if (currentStatus == PULL_STATUS.STATUS_PULL_TO_REFRESH) {
                description.setText(getResources().getString(R.string.pull_to_refresh));
                arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatus == PULL_STATUS.STATUS_RELEASE_TO_REFRESH) {
                description.setText(getResources().getString(R.string.release_to_refresh));
                arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatus == PULL_STATUS.STATUS_REFRESHING) {
                description.setText(getResources().getString(R.string.refreshing));
                progressBar.setVisibility(View.VISIBLE);
                arrow.clearAnimation();
                arrow.setVisibility(View.GONE);
            }
            refreshUpdatedAtValue();
        }
    }

    /**
     * 根据当前的状态来旋转箭头
     */
    private void rotateArrow() {
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        float fromDegrees = 0f;
        float toDegrees = 0f;
        if (currentStatus == PULL_STATUS.STATUS_PULL_TO_REFRESH) {
            fromDegrees = 180f;
            toDegrees = 360f;
        } else if (currentStatus == PULL_STATUS.STATUS_RELEASE_TO_REFRESH) {
            fromDegrees = 0f;
            toDegrees = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees, pivotX, pivotY);
        animation.setDuration(100);
        animation.setFillAfter(true);
        arrow.startAnimation(animation);
    }

    /**
     * 刷新下拉头中上次更新时间的文字描述
     */
    private void refreshUpdatedAtValue() {
        lastUpdateTime = preferences.getLong(UPDATED_AT + mId, -1);
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastUpdateTime;
        long timeIntoFormat;
        String updateAtValue;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        Date date = new Date(System.currentTimeMillis());

        if (lastUpdateTime == -1) {
            updateAtValue = getResources().getString(R.string.not_updated_yet);
        } else if (timePassed < 0) {
            updateAtValue = getResources().getString(R.string.time_error);
        } else if (timePassed < ONE_MINUTE) {
            updateAtValue = getResources().getString(R.string.updated_just_now);
        } else if (timePassed < ONE_HOUR) {
//            timeIntoFormat = timePassed / ONE_MINUTE;
//            String value = timeIntoFormat + "分钟";
//            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
//        } else if (timePassed < ONE_DAY) {
//            timeIntoFormat = timePassed / ONE_HOUR;
//            String value = timeIntoFormat + "小时";
//            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
            updateAtValue=String.format(getResources().getString(R.string.updated_at), simpleDateFormat.format(date));
        } else if (timePassed < ONE_MONTH) {
            timeIntoFormat = timePassed / ONE_DAY;
            String value = timeIntoFormat + "天前";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_YEAR) {
            timeIntoFormat = timePassed / ONE_MONTH;
            String value = timeIntoFormat + "个月前";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else {
            timeIntoFormat = timePassed / ONE_YEAR;
            String value = timeIntoFormat + "年前";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        }
        updateAt.setText(updateAtValue);
    }
    /**
     * 正在刷新的任务，在此任务中会去回调注册进来的下拉刷新监听器
     */
    class RefreshingTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            int topMargin = headerLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= 0) {
                    topMargin = 0;
                    break;
                }
                publishProgress(topMargin);
                SystemClock.sleep(10);
            }
            currentStatus = PULL_STATUS.STATUS_REFRESHING;
            publishProgress(0);
            if (mListener != null) {
                mListener.onRefresh();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            updateHeaderView();
            headerLayoutParams.topMargin = topMargin[0];
            header.setLayoutParams(headerLayoutParams);
        }

    }

    /**
     * 隐藏下拉头的任务，当未进行下拉刷新或下拉刷新完成后，此任务将会使下拉头重新隐藏
     */
    class HideHeaderTask extends AsyncTask<Void, Integer, Integer> {

        @Override
        protected Integer doInBackground(Void... params) {
            int topMargin = headerLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= hideHeaderHeight) {
                    topMargin = hideHeaderHeight;
                    break;
                }
                publishProgress(topMargin);
                SystemClock.sleep(10);
            }
            return topMargin;
        }

        @Override
        protected void onProgressUpdate(Integer ... topMargin) {
            headerLayoutParams.topMargin = topMargin[0];
            header.setLayoutParams(headerLayoutParams);
        }

        @Override
        protected void onPostExecute(Integer topMargin) {
            headerLayoutParams.topMargin = topMargin;
            header.setLayoutParams(headerLayoutParams);
            currentStatus = PULL_STATUS.STATUS_REFRESH_FINISHED;
        }
    }

    /**
     * 下拉刷新的监听器，使用下拉刷新的地方应该注册此监听器来获取刷新回调
     */
    public interface PullToRefreshListener {
        // 刷新时会去回调此方法，在方法内编写具体的刷新逻辑。注意此方法是在子线程中调用的， 可以不必另开线程来进行耗时操作
        void onRefresh();
    }
}