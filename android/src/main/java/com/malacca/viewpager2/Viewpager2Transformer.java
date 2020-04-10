package com.malacca.viewpager2;

import android.view.View;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import com.github.malacca.widget.ViewPager2;

public class Viewpager2Transformer implements ViewPager2.PageTransformer {
    private static final float PIVOT_CENTER = 0.5f;
    private SparseArray<View> pageViews = new SparseArray<>();
    private boolean pageHorizontal;
    private String pageTransformer;
    private float MIN_SCALE = 0.85f;
    private float MIN_ALPHA = 0.85f;
    private float PAGE_PADDING = 0f;
    private float PAGE_MARGIN = 0f;

    static boolean supportTransformer(String transformer) {
        return transformer.equals("card") || transformer.equals("zoomOut")
                || transformer.equals("depth");
    }

    static boolean supportPadding(String transformer) {
        return transformer.equals("card");
    }

    Viewpager2Transformer(boolean horizontal, String transformer) {
        pageHorizontal = horizontal;
        pageTransformer = transformer;
    }

    void setPageHorizontal(boolean horizontal) {
        if (horizontal != pageHorizontal) {
            pageHorizontal = horizontal;
            resetTransformViews();
        }
    }

    void setPageTransformer(@NonNull String transformer) {
        if (!transformer.equals(pageTransformer)) {
            pageTransformer = transformer;
            resetTransformViews();
        }
    }

    void setMinScale(float scale) {
        if (scale < 0) {
            switch (pageTransformer) {
                case "depth":
                    scale = 0.75f;
                    break;
                default:
                    scale = 0.85f;
                    break;
            }
        }
        MIN_SCALE = scale;
    }

    void setMinAlpha(float alpha) {
        if (alpha < 0) {
            switch (pageTransformer) {
                case "zoomOut":
                    alpha = 0.5f;
                    break;
                default:
                    alpha = 1f;
                    break;
            }
        }
        MIN_ALPHA = alpha;
    }

    void setPageMargin(float margin) {
        PAGE_MARGIN = Math.max(0, margin);
    }

    void setPagePadding(float padding) {
        PAGE_PADDING = padding;
    }

    float getPagePadding() {
        return supportPadding(pageTransformer) ? PAGE_PADDING : 0f;
    }

    // 修改 horizontal 或 transformer, 先复原 view 属性
    void resetTransformViews() {
        View view;
        for(int i = 0, size = pageViews.size(); i < size; i++) {
            view = pageViews.valueAt(i);
            if (view.getAlpha() != 1f) {
                view.setAlpha(1f);
            }
            if (view.getScaleX() != 1f) {
                view.setScaleX(1f);
            }
            if (view.getScaleY() != 1f) {
                view.setScaleY(1f);
            }
            if (view.getTranslationX() != 0f) {
                view.setTranslationX(0f);
            }
            if (view.getTranslationY() != 0f) {
                view.setTranslationY(0f);
            }
            view.setPivotX((float) view.getWidth() / 2);
            view.setPivotY((float) view.getHeight() / 2);
        }
    }

    @Override
    public void transformPage(@NonNull View view, float position) {
        int viewId = view.getId();
        if (pageViews.indexOfKey(viewId) < 0) {
            pageViews.put(viewId, view);
        }
        switch (pageTransformer) {
            case "card":
                transformPageCard(view, position);
                break;
            case "zoomOut":
                transformPageZoomOut(view, position);
                break;
            case "depth":
                transformPageDepth(view, position);
                break;
        }
    }

    /**
     * 一屏3个, 两端可设置缩放/透明度
     * 适合做轮播图, 会员卡展示等
     */
    private void transformPageCard(View view, float position) {
        // 两侧 view 缩放比例
        if (MIN_SCALE < 1) {
            int pageWidth = view.getWidth();
            int pageHeight = view.getHeight();
            float scale = MIN_SCALE;
            float pivotX = (float) pageWidth / 2;
            float pivotY = (float) pageHeight / 2;
            if (position < -1) {
                if (pageHorizontal) {
                    pivotX = (float) pageWidth;
                } else {
                    pivotY = (float) pageHeight;
                }
            } else if (position <= 1) {
                scale = (1 - Math.abs(position)) * (1 - MIN_SCALE) + MIN_SCALE;
                if (pageHorizontal) {
                    pivotX = pageWidth * ((1 - position) * PIVOT_CENTER);
                } else {
                    pivotY = pageHeight * ((1 - position) * PIVOT_CENTER);
                }
            } else {
                if (pageHorizontal) {
                    pivotX = 0f;
                } else {
                    pivotY = 0f;
                }
            }
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setPivotX(pivotX);
            view.setPivotY(pivotY);
        }
        // 两侧 view 透明度
        if (MIN_ALPHA < 1) {
            float alpha = position >= -1 && position <= 1
                    ? (1 - Math.abs(position)) * (1 - MIN_ALPHA) + MIN_ALPHA
                    : MIN_ALPHA;
            view.setAlpha(alpha);
        }
        // 在 padding>0 的情况下, 两侧 view 可显示一部分
        if (PAGE_PADDING > 0) {
            float margin = (PAGE_MARGIN > 0f ? PAGE_MARGIN : PAGE_PADDING / 2) * position;
            if (pageHorizontal) {
                view.setTranslationX(margin);
            } else {
                view.setTranslationY(margin);
            }
        }
    }

    // zoomOut 效果
    //https://developer.android.com/training/animation/screen-slide-2#zoom-out
    private void transformPageZoomOut(View view, float position) {
        if (position < -1 || position > 1) {
            view.setAlpha(0f);
            return;
        }
        int pageWidth = view.getWidth();
        int pageHeight = view.getHeight();
        float scale = Math.max(MIN_SCALE, 1 - Math.abs(position));
        float vMargin = pageHeight * (1 - scale) / 2;
        float hMargin = pageWidth * (1 - scale) / 2;
        int mod = position < 0 ? -1 : 1;
        view.setScaleX(scale);
        view.setScaleY(scale);
        if (pageHorizontal) {
            view.setTranslationX(mod * (vMargin / 2 - hMargin));
        } else {
            view.setTranslationY(mod * (hMargin / 2 - vMargin));
        }
        view.setAlpha(MIN_ALPHA + (scale - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));
    }

    //Depth
    //https://developer.android.com/training/animation/screen-slide-2#depth-page
    private void transformPageDepth(View view, float position) {
        if (position < -1) { // [-Infinity,-1)
            // This page is way off-screen to the left.
            view.setAlpha(0f);

        } else if (position <= 0) { // [-1,0]
            // Use the default slide transition when moving to the left page
            view.setAlpha(1f);
            if (pageHorizontal) {
                view.setTranslationX(0f);
            } else {
                view.setTranslationY(0f);
            }
            view.setScaleX(1f);
            view.setScaleY(1f);

        } else if (position <= 1) { // (0,1]
            // Fade the page out.
            view.setAlpha(1 - position);
            // Counteract the default slide transition
            float offset = -position * (pageHorizontal ? view.getWidth() : view.getHeight());
            if (pageHorizontal) {
                view.setTranslationX(offset);
            } else {
                view.setTranslationY(offset);
            }
            // Scale the page down (between MIN_SCALE and 1)
            float scale = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
            view.setScaleX(scale);
            view.setScaleY(scale);
        } else { // (1,+Infinity]
            // This page is way off-screen to the right.
            view.setAlpha(0f);
        }
    }
}
