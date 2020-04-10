package com.malacca.viewpager2;

import java.util.List;
import java.util.ArrayList;

import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.ViewCompat;
import android.util.SparseArray;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import com.github.malacca.widget.ViewPager2;

public class Viewpager2Adapter extends RecyclerView.Adapter<Viewpager2Adapter.ViewHolder> {
    static class ViewHolder extends RecyclerView.ViewHolder {
        int holderId = -1;
        ViewHolder(View view) {
            super(view);
        }
    }

    // 动态转换 horizontal 或 Transformer 会导致 selectedItem 前后不一致
    // 这里缓存一下转换前的 selectedItem 以便转换完成后进行修正
    int lastSelectedItem = -1;

    private ViewPager2 mViewpager2;
    private ReadableMap eventListeners;
    private RCTEventEmitter mEventEmitter;
    private Boolean itemIsChild;
    private Boolean withBackgroundView;

    private final List<View> mViews = new ArrayList<>();
    private SparseArray<ViewHolder> recycledViewHolders;
    private SparseArray<ViewHolder> emptyViewHolders;
    private SparseArray<Integer> mViewPositions;
    private View backgroundView;
    private int currentHolderId = 0;
    private int itemCount = 0;

    Viewpager2Adapter(ThemedReactContext reactContext, ViewPager2 viewpager2) {
        mEventEmitter = reactContext.getJSModule(RCTEventEmitter.class);
        mViewpager2 = viewpager2;
    }

    // 修改切换效果前, 记录 selectedItem
    boolean setLastSelectedItem() {
        if (currentHolderId > 0) {
            lastSelectedItem = mViewpager2.getCurrentItem();
            return true;
        }
        return false;
    }

    // 是否直接使用子 view, 只能设置一次, 不能动态修改
    void setItemIsChild(boolean enable) {
        if (itemIsChild == null) {
            itemIsChild = enable;
        }
    }

    private boolean getItemIsChild() {
        return itemIsChild != null && itemIsChild;
    }

    // 是否使用 backgroundView, 不能动态修改
    void setWithBackgroundView(boolean with) {
        if (withBackgroundView == null) {
            withBackgroundView = with;
        }
    }

    private boolean isWithBackgroundView() {
        return withBackgroundView != null && withBackgroundView;
    }

    void addView(View child, int index) {
        if (isWithBackgroundView()) {
            if(index == 0) {
                backgroundView = child;
                return;
            }
            index--;
        }
        mViews.add(child);
        if (getItemIsChild()) {
            notifyItemInserted(index);
        } else {
            bindFailedViewHolder();
        }
    }

    View getChildAt(int index) {
        if (isWithBackgroundView()) {
            if (index == 0) {
                return backgroundView;
            }
            index--;
        }
        return mViews.get(index);
    }

    void removeViewAt(int index) {
        if (isWithBackgroundView()) {
            if (index == 0) {
                backgroundView = null;
                return;
            }
            index--;
        }
        mViews.remove(index);
        if (getItemIsChild()) {
            notifyItemRemoved(index);
        }
    }

    int getChildCount() {
        return mViews.size() + (isWithBackgroundView() && backgroundView != null ? 1 : 0);
    }

    // 不使用 子view 作为 child 的, 手动设置子 view 个数
    void setItemCount(int count) {
        if (count == itemCount) {
            return;
        }
        int oldCount = itemCount;
        itemCount = count;
        if (count > oldCount) {
            notifyItemRangeInserted(oldCount, count - oldCount);
        } else {
            notifyItemRangeRemoved(count, oldCount - count);
        }
    }

    @Override
    public int getItemCount() {
        return getItemIsChild() ? mViews.size() : itemCount;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        container.setId(ViewCompat.generateViewId());
        container.setSaveEnabled(false);
        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (getItemIsChild()) {
            bindStaticViewHolder(holder, position);
        } else {
            bindDynamicViewHolder(holder, position);
        }
    }

    // 绑定静态 预加载view (viewpager item 直接使用子 view)
    private void bindStaticViewHolder(@NonNull ViewHolder holder, int position) {
        if (currentHolderId == 0) {
            // 避免影响 setLastSelectedItem 函数执行, 设置为 1
            currentHolderId = 1;
        }
        FrameLayout container = (FrameLayout) holder.itemView;
        if (container.getChildCount() != 0) {
            // 刚好命中, 啥都不做
            if (holder.holderId == position) {
                return;
            }
            // 移除子view
            container.removeAllViews();
        }
        // 挂载当前 position 的子 view, 但该 子view 是有可能已挂载到其他 viewHolder 了
        // 需要将 子view 释放, 确保 子view 没有 parent
        View view = mViews.get(position);
        FrameLayout frameLayout = (FrameLayout) view.getParent();
        if (frameLayout != null) {
            frameLayout.removeAllViews();
        }
        holder.holderId = position;
        container.addView(view);
    }

    // 绑定动态 预加载view (viewpager item 重复使用, 动态更新)
    @SuppressLint("UseSparseArrays")
    private boolean bindDynamicViewHolder(@NonNull ViewHolder holder, int position) {
        FrameLayout container = (FrameLayout) holder.itemView;
        int from = holder.holderId;
        if (from == -1) {
            if (currentHolderId < mViews.size()) {
                // 够用, 直接使用预加载的 子view
                from = holder.holderId = currentHolderId;
                container.addView(mViews.get(from));
                currentHolderId++;
            } else if (recycledViewHolders != null && recycledViewHolders.size() > 0) {
                // 尝试从回收的 recycledViewHolder 中提取 子view
                int recycledHolderId = recycledViewHolders.keyAt(0);
                ViewHolder recycledHolder = recycledViewHolders.get(recycledHolderId);
                recycledHolder.holderId = -1;
                ((FrameLayout) recycledHolder.itemView).removeAllViews();
                recycledViewHolders.remove(recycledHolderId);
                from = holder.holderId = recycledHolderId;
                container.addView(mViews.get(from));
            } else {
                // 以上两种方案都失败了(有可能), 使用兜底方案, 通知 js 再创建一个 子view
                if (emptyViewHolders == null) {
                    emptyViewHolders = new SparseArray<>();
                }
                emptyViewHolders.put(position, holder);
                WritableMap event = Arguments.createMap();
                event.putString("event", "addViewHolder");
                sendEvent(event);
            }
        }
        if (from == -1) {
            return false;
        }
        if (isWithBackgroundView()) {
            if (mViewPositions == null) {
                mViewPositions = new SparseArray<>();
            }
            mViewPositions.put(position, from);
        }
        WritableMap event = Arguments.createMap();
        event.putString("event", "bindViewHolder");
        event.putInt("from", from);
        event.putInt("to", position);
        sendEvent(event);
        return true;
    }

    // bindDynamicViewHolder 失败后通知 js 创建 子view
    // 这里将收到新创建的 子view 绑定到之前的 emptyViewHolder
    private void bindFailedViewHolder() {
        if (emptyViewHolders == null || emptyViewHolders.size() == 0) {
            return;
        }
        int position = emptyViewHolders.keyAt(0);
        ViewHolder viewHolder = emptyViewHolders.get(position);
        if (bindDynamicViewHolder(viewHolder, position)) {
            emptyViewHolders.remove(position);
        }
    }

    /**
     * 在 count 发生变化 或 滑动过程中, viewHolder 可能会被被回收
     * 一般被回收的 viewHolder 会再次 onViewAttachedToWindow 重复使用
     * 但也有可能不会重复使用了, 而是重新调用 onCreateViewHolder 创建新的 frameLayout
     * 由于这个不会重复使用的 viewHolder 占用了 mViews
     * 导致在 onBindViewHolder 时找不到可用的 mViews
     * 所以这里缓存一下, 以便在 onBindViewHolder 可提取 mViews
     */
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        if (getItemIsChild()) {
            return;
        }
        FrameLayout frameLayout = holder.holderId == -1 ? null : (FrameLayout) holder.itemView;
        if (frameLayout == null) {
            return;
        }
        if (recycledViewHolders == null) {
            recycledViewHolders = new SparseArray<>();
        }
        recycledViewHolders.put(holder.holderId, holder);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        if (getItemIsChild() || recycledViewHolders == null
                || recycledViewHolders.indexOfKey(holder.holderId) < 0) {
            return;
        }
        recycledViewHolders.remove(holder.holderId);
    }

    // 移动 backgroundView 到当前 item
    void bindBackgroundView(int position) {
        if (!isWithBackgroundView()) {
            return;
        }
        if (!getItemIsChild()) {
            if (mViewPositions == null) {
                return;
            }
            position = mViewPositions.get(position);
        }
        View view = mViews.get(position);
        if (view == null) {
            return;
        }
        FrameLayout backgroundParent = (FrameLayout) backgroundView.getParent();
        if (backgroundParent != null) {
            backgroundParent.removeView(backgroundView);
        }
        FrameLayout parent = (FrameLayout) view.getParent();
        if (parent != null) {
            parent.addView(backgroundView, 0);
        }
    }

    // 给 js 端发消息
    void setPageScrollListener(ReadableMap listeners) {
        eventListeners = listeners;
    }

    void sendPageScrollEvent(WritableMap event) {
        String eventType = event.getString("event");
        if (eventType == null || !eventListeners.hasKey(eventType) || !eventListeners.getBoolean(eventType)) {
            return;
        }
        event.putBoolean("fake", mViewpager2.isFakeDragging());
        sendEvent(event);
    }

    void sendEvent(WritableMap event) {
        mEventEmitter.receiveEvent(mViewpager2.getId(), Viewpager2Manager.EVENT_NAME, event);
    }
}