package me.ele.uetool;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.*;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.jakewharton.scalpel.ScalpelFrameLayout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static me.ele.uetool.TransparentActivity.Type.TYPE_UNKNOWN;

public class UETMenu extends LinearLayout {

    private View vMenu;
    private View vSubMenuClip;
    private ViewGroup vSubMenuContainer;
    private ValueAnimator animator;
    private Interpolator defaultInterpolator = new AccelerateDecelerateInterpolator();
    private List<UETSubMenu.SubMenu> subMenus = new ArrayList<>();

    private WindowManager windowManager;
    private WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    private int touchSlop;
    private int y;
    /**
     * 容器刚出来的时候的宽度，用于播放动画
     */
    private int vSubMenuContainerWidth = 0;
    private boolean isSubMenuOpen = false;

    public UETMenu(final Context context, int y) {
        super(context);
        inflate(context, R.layout.uet_menu_layout, this);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        this.y = y;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        vMenu = findViewById(R.id.menu);
        vSubMenuClip = findViewById(R.id.sub_menu_clip);
        vSubMenuContainer = findViewById(R.id.sub_menu_container);
        Resources resources = context.getResources();
        subMenus.add(new UETSubMenu.SubMenu(resources.getString(R.string.uet_catch_view), R.drawable.uet_edit_attr, new OnClickListener() {
            @Override
            public void onClick(View v) {
                open(TransparentActivity.Type.TYPE_EDIT_ATTR);
            }
        }));
        subMenus.add(new UETSubMenu.SubMenu(resources.getString(R.string.uet_relative_location), R.drawable.uet_relative_position,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(TransparentActivity.Type.TYPE_RELATIVE_POSITION);
                    }
                }));
        subMenus.add(new UETSubMenu.SubMenu(resources.getString(R.string.uet_grid), R.drawable.uet_show_gridding,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(TransparentActivity.Type.TYPE_SHOW_GRIDDING);
                    }
                }));

        subMenus.add(new UETSubMenu.SubMenu(resources.getString(R.string.uet_scalpel), R.drawable.uet_scalpel, new OnClickListener() {
            @Override
            public void onClick(View view) {
                ViewGroup decorView = (ViewGroup) Util.getCurrentActivity().getWindow().getDecorView();
                ViewGroup content = decorView.findViewById(android.R.id.content);
                View contentChild = content.getChildAt(0);
                if (contentChild != null) {
                    if (contentChild instanceof ScalpelFrameLayout) {
                        content.removeAllViews();
                        View originContent = ((ScalpelFrameLayout) contentChild).getChildAt(0);
                        ((ScalpelFrameLayout) contentChild).removeAllViews();
                        content.addView(originContent);
                    } else {
                        content.removeAllViews();
                        ScalpelFrameLayout frameLayout = new ScalpelFrameLayout(getContext());
                        frameLayout.setLayerInteractionEnabled(true);
                        frameLayout.setDrawIds(true);
                        frameLayout.addView(contentChild);
                        content.addView(frameLayout);
                    }
                }
            }
        }));

        for (UETSubMenu.SubMenu subMenu : subMenus) {
            UETSubMenu uetSubMenu = new UETSubMenu(getContext());
            uetSubMenu.update(subMenu);
            vSubMenuContainer.addView(uetSubMenu);
        }

        vMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startAnim();
            }
        });

        vMenu.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private float lastY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        lastY = downY;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        params.y += event.getRawY() - lastY;
                        params.y = Math.max(0, params.y);
                        windowManager.updateViewLayout(UETMenu.this, params);
                        lastY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getRawX() - downX) < touchSlop && Math.abs(event.getRawY() - downY) < touchSlop) {
                            try {
                                Field field = View.class.getDeclaredField("mListenerInfo");
                                field.setAccessible(true);
                                Object object = field.get(vMenu);
                                field = object.getClass().getDeclaredField("mOnClickListener");
                                field.setAccessible(true);
                                object = field.get(object);
                                if (object != null && object instanceof View.OnClickListener) {
                                    ((View.OnClickListener) object).onClick(vMenu);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                }
                return true;
            }
        });

        // 获取容器宽度，同时初始化设置
        vSubMenuContainer.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                vSubMenuContainerWidth = vSubMenuContainer.getMeasuredWidth();
                setSubMenuContainerWidth(vSubMenuContainerWidth);
                setSubMenuClipWidth(0);
                vSubMenuContainer.setTranslationX(-vSubMenuContainerWidth); // 隐藏
                vSubMenuClip.setVisibility(View.GONE); // 设置为不可见，移除父容器占位
                vSubMenuContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                // 这次不需要绘制，避免闪烁
                return false;
            }
        });
    }

    private void startAnim() {
        if (vSubMenuContainerWidth <= 0 || (animator != null && animator.isRunning())) {
            return;
        }
        ensureAnim();
        final boolean open = !isSubMenuOpen;
        animator.setIntValues(open ? 0 : vSubMenuContainerWidth, open ? vSubMenuContainerWidth : 0);
        animator.setInterpolator(defaultInterpolator);
        animator.removeAllListeners();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                vSubMenuClip.setVisibility(VISIBLE);
                setSubMenuClipWidth(open ? 0 : vSubMenuContainerWidth);
                vSubMenuContainer.setTranslationX(open ? -vSubMenuContainerWidth : 0);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isSubMenuOpen = open;
                if (!isSubMenuOpen) {
                    vSubMenuClip.setVisibility(GONE);
                }
            }
        });
        animator.start();
    }

    private void ensureAnim() {
        if (animator == null) {
            animator = ValueAnimator.ofInt(0, vSubMenuContainerWidth);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int width = (int) animation.getAnimatedValue();
                    setSubMenuClipWidth(width);
                    vSubMenuContainer.setTranslationX(width - vSubMenuContainerWidth);
                }
            });
            animator.setDuration(400);
        }
    }

    private void setSubMenuClipWidth(int width) {
        ViewGroup.LayoutParams layoutParams = vSubMenuClip.getLayoutParams();
        if (layoutParams.width != width) {
            layoutParams.width = width;
            vSubMenuClip.setLayoutParams(layoutParams);
        }
    }

    private void setSubMenuContainerWidth(int width) {
        ViewGroup.LayoutParams layoutParams = vSubMenuContainer.getLayoutParams();
        if (layoutParams.width != width) {
            layoutParams.width = width;
            vSubMenuContainer.setLayoutParams(layoutParams);
        }
    }

    private void open() {
        open(TYPE_UNKNOWN);
    }

    private void open(@TransparentActivity.Type int type) {
        Activity currentTopActivity = Util.getCurrentActivity();
        if (currentTopActivity == null) {
            return;
        } else if (currentTopActivity.getClass() == TransparentActivity.class) {
            currentTopActivity.finish();
            return;
        }
        Intent intent = new Intent(currentTopActivity, TransparentActivity.class);
        intent.putExtra(TransparentActivity.EXTRA_TYPE, type);
        currentTopActivity.startActivity(intent);
        currentTopActivity.overridePendingTransition(0, 0);
        UETool.getInstance().setTargetActivity(currentTopActivity);
    }

    public void show() {
        try {
            windowManager.addView(this, getWindowLayoutParams());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int dismiss() {
        try {
            windowManager.removeView(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return params.y;
    }

    private WindowManager.LayoutParams getWindowLayoutParams() {
        params.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        params.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 10;
        params.y = y;
        return params;
    }

}
