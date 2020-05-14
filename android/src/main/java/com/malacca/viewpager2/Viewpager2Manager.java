package com.malacca.viewpager2;

import java.util.Map;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

import com.github.malacca.widget.ViewPager2;

public class Viewpager2Manager extends ViewGroupManager<ViewPager2> {
    static final String EVENT_NAME = "onViewpager2Event";
    static final String EVENT_ON_SCROLL = "onPageScroll";
    private static final String REACT_CLASS = "RNViewpager2";

    @NonNull
    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public boolean needsCustomLayoutForChildren() {
        return true;
    }

    @Override
    public @Nullable Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.<String, Object>builder()
                .put(EVENT_NAME, MapBuilder.of("registrationName", EVENT_NAME))
                .put(EVENT_ON_SCROLL, MapBuilder.of("registrationName", EVENT_ON_SCROLL))
                .build();
    }

    @NonNull
    @Override
    protected ViewPager2 createViewInstance(@NonNull ThemedReactContext reactContext) {
        final ViewPager2 vp = new ViewPager2(reactContext);
        final Viewpager2Adapter adapter = new Viewpager2Adapter(reactContext, vp);
        vp.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        vp.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        vp.setAdapter(adapter);
        vp.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            private int scrollState = -1;
            private int scrollPosition = -1;
            private int lastPosition = -1;

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                adapter.sendPageScrollEvent("onPageScrollStateChanged", state);
                if (state == 0) {
                    scrollState = -1;
                    if (scrollPosition != -1) {
                        onPageChanged(scrollPosition);
                        scrollPosition = -1;
                    }
                } else {
                    scrollState = state;
                }
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                adapter.sendPageScrollEvent(position, positionOffset, positionOffsetPixels);
            }

            // 已选中 page, 但还有一段惯性滑动
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                adapter.sendPageScrollEvent("onPageSelected", position);
                if (scrollState == -1) {
                    onPageChanged(position);
                } else {
                    scrollPosition = position;
                }
            }

            // 滑动结束, 切换完成
            private void onPageChanged(int position) {
                if (position == lastPosition) {
                    return;
                }
                lastPosition = position;
                adapter.sendPageScrollEvent("onPageChanged", position);
                if (!scrollToLastSelectedItem(vp, adapter)) {
                    adapter.bindBackgroundView(position);
                }
            }
        });
        return vp;
    }

    // 减少不必要通信, 指定需要监听的回调
    @ReactProp(name = "listeners")
    public void setListeners(ViewPager2 view, ReadableMap config) {
        final Viewpager2Adapter adapter = (Viewpager2Adapter) view.getAdapter();
        if (adapter != null) {
            adapter.setPageScrollListener(config);
        }
    }

    // 是否直接使用子 view, 不可中途修改
    @ReactProp(name = "itemIsChild")
    public void setItemIsChild(ViewPager2 view, boolean itemIsChild) {
        Viewpager2Adapter adapter = (Viewpager2Adapter) view.getAdapter();
        if (adapter != null) {
            adapter.setItemIsChild(itemIsChild);
        }
    }

    // 为 实现类似抖音 效果的参数, 第一个 子view 将作为其他 子view 的背景 view 加载
    // 且总是显示到 currentItem 中
    @ReactProp(name = "withBackgroundView")
    public void setWithBackgroundView(ViewPager2 view, boolean withBackgroundView) {
        Viewpager2Adapter adapter = (Viewpager2Adapter) view.getAdapter();
        if (adapter != null) {
            adapter.setWithBackgroundView(withBackgroundView);
        }
    }

    // 在滑动到第一个或最后一个时 不显示水波纹效果
    @ReactProp(name = "disableWave")
    public void setDisableWave(ViewPager2 view, boolean disableWave) {
        RecyclerView recyclerView = (RecyclerView) view.getChildAt(0);
        recyclerView.setOverScrollMode(disableWave ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_ALWAYS);
    }

    // 禁用
    @ReactProp(name = "disableSwipe")
    public void setDisableSwipe(ViewPager2 view, boolean disableSwipe) {
        view.setUserInputEnabled(!disableSwipe);
    }

    // 当前显示
    @ReactProp(name = "currentIndex")
    public void setCurrentItem(ViewPager2 view, int currentIndex) {
        scrollToIndex(view, currentIndex, false);
    }

    // 离屏预加载个数 (使用子view的情况, 可中途修改)
    @ReactProp(name = "offscreenPageLimit", defaultInt = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT)
    public void setOffscreenPageLimit(ViewPager2 view, int limit) {
        view.setOffscreenPageLimit(limit == 0 ? ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT : limit);
    }

    // 方向
    @ReactProp(name = "horizontal")
    public void setHorizontal(ViewPager2 view, boolean horizontal) {
        recordSelectedItem(view);
        view.setOrientation(horizontal ? ViewPager2.ORIENTATION_HORIZONTAL : ViewPager2.ORIENTATION_VERTICAL);
        // 修改方向后通知 transformer (如果指定的话)
        Viewpager2Transformer transformer = (Viewpager2Transformer) view.getPageTransformer();
        if (transformer != null) {
            int oldPadding = (int) transformer.getPagePadding();
            if (oldPadding >= 0f) {
                updateRecyclerPadding(view, oldPadding, horizontal);
            }
            transformer.setPageHorizontal(horizontal);
        }
    }

    // 切换效果
    @ReactProp(name = "transformer")
    public void setTransformer(ViewPager2 view, ReadableMap config) {
        Viewpager2Transformer transformer = (Viewpager2Transformer) view.getPageTransformer();
        boolean isHorizontal = view.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL;
        float oldPadding = transformer != null ? transformer.getPagePadding() : 0f;

        String type = config.hasKey("type") ? config.getString("type") : null;
        type = type != null && Viewpager2Transformer.supportTransformer(type) ? type : null;
        // 使用默认转换效果
        if (type == null) {
            if (transformer != null) {
                recordSelectedItem(view);
                if (oldPadding != 0f) {
                    updateRecyclerPadding(view, 0, isHorizontal);
                }
                transformer.resetTransformViews();
                view.setPageTransformer(null);
            }
            return;
        }
        // 使用预置效果
        recordSelectedItem(view);
        boolean hasTransformer = transformer != null;
        if (!hasTransformer) {
            transformer = new Viewpager2Transformer(isHorizontal, type);
        }
        transformer.setMinAlpha(config.hasKey("alpha")
                ? (float) config.getDouble("alpha")
                : -1f
        );
        transformer.setMinScale(config.hasKey("scale")
                ? (float) config.getDouble("scale")
                : -1f
        );
        transformer.setPageMargin(config.hasKey("margin")
                ? PixelUtil.toPixelFromDIP(config.getInt("margin"))
                : -1f
        );
        float padding = 0f;
        if (Viewpager2Transformer.supportPadding(type)) {
            transformer.setPagePadding(padding = config.hasKey("padding")
                    ? PixelUtil.toPixelFromDIP(config.getInt("padding"))
                    : 0f
            );
        }
        if (padding != oldPadding) {
            updateRecyclerPadding(view, (int) padding, isHorizontal);
        }
        if (!hasTransformer) {
            view.setPageTransformer(transformer);
        } else {
            transformer.setPageTransformer(type);
        }
    }

    // 一页显示多个的情况, 通过设置 recyclerView padding 来实现
    private void updateRecyclerPadding(ViewPager2 view, int padding, boolean horizontal) {
        RecyclerView recyclerView = (RecyclerView) view.getChildAt(0);
        if (recyclerView.getClipToPadding()) {
            recyclerView.setClipToPadding(false);
        }
        if (padding > 0) {
            if (horizontal) {
                recyclerView.setPadding(padding, 0, padding, 0);
            } else {
                recyclerView.setPadding(0, padding, 0, padding);
            }
        } else {
            recyclerView.setPadding(0, 0, 0, 0);
        }
    }

    /**
     * horizontal 或 transformer 属性变动时, 可能会导致修改后选中 pageItem 与修改前不一致
     * 还有一些些情况 (比如从一屏多个到一屏一个), 虽然修改前后的 pageItem 一致
     * 但会出现不符合预期的效果, 比如本应一屏一个, 却显示两个, 这种情况通过 FakeDrag 修正
     * 所以在修改这两个属性前, 记录当前 viewpager 选中的 item
     * 1. 但转换完成后, 可能会触发 onPageSelected 回调, 在回调中会重新选择记录的 currentItem
     *   一旦 onPageSelected 被触发, 就会在完成属性转变后立即修正, 此处的 runnable 就会跳过执行
     * 2. 若未触发 onPageSelected, 会执行这里的 runnable,
     *   但这里是最为上面方案的兜底措施, 因为这里执行可能会略有延迟
     */
    private void recordSelectedItem(final ViewPager2 view) {
        final Viewpager2Adapter adapter = (Viewpager2Adapter) view.getAdapter();
        if (adapter == null || !adapter.setLastSelectedItem()) {
            return;
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollToLastSelectedItem(view, adapter);
            }
        }, 10);
    }

    // horizontal 或 transformer 变动后的修正
    private boolean scrollToLastSelectedItem(ViewPager2 view, Viewpager2Adapter adapter) {
        int lastSelectedItem = adapter.lastSelectedItem;
        if (lastSelectedItem == -1) {
            return false;
        }
        adapter.lastSelectedItem = -1;
        if (lastSelectedItem == view.getCurrentItem()) {
            // todo
            // 实测发现, getCurrentItem() 可能返回值总是等于 lastSelectedItem
            // 实际并不是, 此时 fakeDrag 并不能修正到之前的 lastSelectedItem
            // 但这个修正仍然是有必要行的, 前面对于一屏多个的可以让其滑动到最终状态
            // 这可能是 viewpager2 的一个bug, 不晓得是否有其他方式可弥补这个不足, 以达到目的
            view.beginFakeDrag();
            view.fakeDragBy(1);
            view.endFakeDrag();
        } else {
            scrollToIndex(view, lastSelectedItem, false);
        }
        return true;
    }

    // 处理 js 端发送的命令
    @Override
    public void receiveCommand(@NonNull final ViewPager2 view, String commandId, @Nullable ReadableArray args) {
        Viewpager2Adapter adapter = args == null || args.size() < 1 ? null : (Viewpager2Adapter) view.getAdapter();
        if (adapter == null) {
            return;
        }
        switch (commandId) {
            case "setCount":
                int selected = args.size() > 1 ? args.getInt(1) : -1;
                boolean scrollBefore = selected < adapter.getItemCount();
                if (selected != -1 && scrollBefore) {
                    scrollToIndex(view, selected, false);
                }
                adapter.setItemCount(args.getInt(0));
                if (!scrollBefore) {
                    scrollToIndex(view, selected, false);
                }
                break;
            case "insertCount":
                adapter.insertItemRange(args.getInt(0), args.getInt(1));
                break;
            case "removeCount":
                adapter.removeItemRange(args.getInt(0), args.getInt(1));
                break;
            case "setCurrentIndex":
                scrollToIndex(view, args.getInt(0), args.getBoolean(1));
                break;
            case "getCurrentIndex":
                WritableMap event = Arguments.createMap();
                event.putString("event", "getCurrentIndex");
                event.putString("index", args.getString(0));
                event.putInt("item", view.getCurrentItem());
                adapter.sendEvent(event);
                break;
            case "beginFakeDrag":
                view.beginFakeDrag();
                break;
            case "fakeDragBy":
                view.fakeDragBy(PixelUtil.toPixelFromDIP(args.getDouble(0)));
                break;
            case "endFakeDrag":
                view.endFakeDrag();
                break;
        }
    }

    private void scrollToIndex(@NonNull ViewPager2 view, int index, boolean smoothScroll) {
        view.setCurrentItem(index, smoothScroll);
    }

    @Override
    public void addView(ViewPager2 parent, View child, int index) {
        Viewpager2Adapter adapter = child == null ? null : (Viewpager2Adapter) parent.getAdapter();
        if (adapter != null) {
            adapter.addView(child, index);
        }
    }

    @Override
    public int getChildCount(ViewPager2 parent) {
        Viewpager2Adapter adapter = (Viewpager2Adapter) parent.getAdapter();
        return adapter == null ? 0 : adapter.getChildCount();
    }

    @Override
    public View getChildAt(ViewPager2 parent, int index) {
        Viewpager2Adapter adapter = (Viewpager2Adapter) parent.getAdapter();
        return adapter == null ? null : adapter.getChildAt(index);
    }

    @Override
    public void removeViewAt(ViewPager2 parent, int index) {
        Viewpager2Adapter adapter = (Viewpager2Adapter) parent.getAdapter();
        if (adapter != null) {
            adapter.removeViewAt(index);
        }
    }
}