import React,{PureComponent} from 'react';
import {requireNativeComponent, UIManager, findNodeHandle, StyleSheet, View} from 'react-native';
import splitLayoutProps from 'react-native/Libraries/StyleSheet/splitLayoutProps';

const RNViewpager2 = requireNativeComponent('RNViewpager2');

const TransformerProps = ["alpha", "scale", "padding", "margin"];
function makeTransformer(type, props) {
  const transformer = {
    type
  };
  TransformerProps.forEach(k => {
    if (k in props) {
      const num = props[k];
      if (Number(num) === num) {
        transformer[k] = num;
      }
    }
  })
  return transformer;
}

/**
 * 给 viewpager 设定的 refresh 组件添加一层 wrapper
 * 这样原本的 refresh 组件就不需要管理 refreshing 状态了
 * 只需要绑定 onRefresh 回调, 设置其他支持属性(如 tintColor) 即可
 * 这也的好处是下拉刷新不再读取整个组件树, 而是仅 render 当前 PagerRefresh
 * 另外 PagerRefresh 支持动态 enable 以修正 viewpager 水波纹效果
 * 
 * 所指定的 refresh 组件需要支持以下属性
 * refreshing、enabled、onRefresh
 */
class PagerRefresh extends PureComponent {
  state = {
    refreshing:false,
    enabled:false,
  }
  enabled = (enabled) => {
    if (this.state.enabled !== enabled) {
      this.setState({enabled});
    }
  }
  _endRefresh = () => {
    this.setState({
      refreshing:false
    })
  }
  _onRefresh = () => {
    const onRefresh = this.props.refreshControl.props.onRefresh;
    if (onRefresh) {
      this.setState({
        refreshing:true
      });
      onRefresh(this._endRefresh);
    }
  }
  render(){
    const {refreshControl, ...props} = this.props;
    props.refreshing = this.state.refreshing;
    props.enabled = this.state.enabled;
    props.onRefresh = this._onRefresh;
    return React.cloneElement(refreshControl, props);
  }
}

const eventListenrs = ['onPageScroll', 'onPageScrollStateChanged', 'onPageSelected', 'onPageChanged'];
export default class extends PureComponent {
  _refreshRef = null;
  _backgroundView = null;
  constructor(props) {
    super(props);
    // 背景 view, 仅初始化时有效
    const {getBackground:BackgroundComponent} = props;
    if (BackgroundComponent) {
      this._backgroundView = React.isValidElement(BackgroundComponent) ? (
        BackgroundComponent
      ) : (
        <BackgroundComponent />
      );
    }
  }

  componentWillUnmount(){
    this._stopAutoPlay();
  }

  // 获取 子view 个数
  _childrenCount = 0;
  getCount = () => {
    return this._childrenCount;
  }
  _setCount = count => {
    this._childrenCount = count;
  }

  // 设置选中的 item index (自动修正 loop index)
  setCurrentIndex = (index, smooth) => {
    this._setShowIndex(this._getShowIndex(index), smooth);
  }
  _getShowIndex = (index) => {
    index = parseInt(index||0);
    index = Math.max(0, Math.min(this._childrenCount - 1, index));
    return this._isLoop ? index + 1 : index;
  }
  _setShowIndex = (index, smooth) => {
    this._sendCommand('setCurrentIndex', [index, Boolean(smooth)])
  }

  // 获取当前选中的 item index
  getCurrentIndex = () => {
    return this._scrollPage;
  }

  // 动态设置是否禁用, 相比直接设置 props 性能更好
  disable = (disabled) => {
    this.refs.pager.setNativeProps({
      disableSwipe: Boolean(disabled)
    })
  }

  // 开始拖拽(模拟手指按下)
  beginFakeDrag = () => {
    this._sendCommand('beginFakeDrag', [0]);
  }

  // 设置模拟拖拽偏移值
  fakeDragBy = (offset) => {
    this._sendCommand('fakeDragBy', [parseInt(offset)]);
  }

  // 结束拖拽(模拟手指松开)
  endFakeDrag = () => {
    this._sendCommand('endFakeDrag', [0]);
  }

  // 发送消息给 native 端
  _nodeHandle = null;
  _sendCommand = (command, args) => {
    if (this._nodeHandle === null) {
      this._nodeHandle = findNodeHandle(this.refs.pager);
    }
    UIManager.dispatchViewManagerCommand(this._nodeHandle, command, args);
  }

  // 监听 native 端的消息
  _scrollPage = 0;
  _onViewpager2Event(e) {
    const {event, ...msg} = e.nativeEvent;
    if (event === 'onPageScrollStateChanged') {
      if (msg.state === 0) {
        this._startAutoPlay();
      } else if (msg.state === 1) {
        this._stopAutoPlay();
      }
    } else if (this._isLoop) {
      msg.position = msg.position - 1;
    }
    this.props[event] && this.props[event](msg);
    // loop 模式在滑到边界 index 时, 修正 currentIndex
    if (event === "onPageChanged") {
      if (this._isLoop) {
        if (msg.position < 0) {
          this._setShowIndex(this._childrenCount);
          return;
        } else if (msg.position >= this._childrenCount) {
          this._setShowIndex(1);
          return;
        }
      }
      this._scrollPage = msg.position;
      this._enableRefresh();
    }
  }

  // 禁用/启用 下拉刷新
  _enableRefresh = () => {
    if (this._refreshRef) {
      this._refreshRef.enabled(this._scrollPage === 0)
    }
  }

  // 自动播放
  _isLoop = false;
  _autPlayTimer = null;
  _startAutoPlay = (fromPropsChange) => {
    this._stopAutoPlay();
    if (!this.props.autoplay) {
      return;
    }
    // 这里应该是 android viewpager2 的 bug, 在 props 更新
    // 由 loop 转为 非loop 且当前处于开头第一个 page
    // autoPlay 的下一个 index 为 1, 但 native 端的 currentIndex 也是 1
    // 由于二者相等, 所以自动播放无法启动 (正确情况下 native 段应该是 0 才对)
    // 所以这里通过 fakeDrag 修正一下
    if (fromPropsChange && !this._isLoop && this._scrollPage === 0) {
      this.beginFakeDrag();
      this.fakeDragBy(.5);
      this.endFakeDrag();
    }
    this._autPlayTimer = setTimeout(() => {
      this._autPlayTimer = null;
      let next;
      if (this._scrollPage >= this._childrenCount - 1) {
        if (this.props.autoplayDirection) {
          return;
        }
        next = this._isLoop ? this._childrenCount + 1 : 0;
      } else {
        next = this._scrollPage + (this._isLoop ? 2 : 1);
      }
      this._setShowIndex(next, true);
    }, this.props.autoplayTimeout||2500);
  }
  _stopAutoPlay = () => {
    if (this._autPlayTimer) {
      clearTimeout(this._autPlayTimer);
      this._autPlayTimer = null;
    }
  }

  // 计算 item style
  _itemStyleStatus = null;
  _itemStyleChange = false;
  _itemStyle = {};
  _computeBeforeRender() {
    const {horizontal, transformer="", padding=0, loop} = this.props;
    this._isLoop = loop && this._childrenCount > 1;
    const newStatus = transformer + "_" + padding + "_" + (horizontal ? '1' : '0');
    if (newStatus === this._itemStyleStatus) {
      this._itemStyleChange = false;
      return;
    }
    this._itemStyleStatus = newStatus;
    this._itemStyleChange = true;
    let paddingHorizontal = paddingVertical = 0;
    if (transformer === 'card' && padding > 0) {
      if (horizontal) {
        paddingHorizontal = padding;
      } else {
        paddingVertical = padding;
      }
    }
    this._itemStyle = {
      position: 'absolute',
      left: paddingHorizontal,
      right: paddingHorizontal,
      top: paddingVertical,
      bottom: paddingVertical,
      width: undefined,
      height: undefined,
    }
  }

  _renderViewpager(extraProps) {
    const {
      style,
      autoplay,
      horizontal,
      currentIndex,
      transformer,
      disableSwipe,
      refreshControl,
      ...leftProps
    } = this.props;

    // 边缘水波纹 (loop 模式缺省不显示)
    const disableWave = 'disableWave' in leftProps 
      ? Boolean(leftProps.disableWave)
      : (this._isLoop ? true : undefined);

    // 下拉刷新组件(最起码RN自带的那个) 与 Viewpager 有冲突
    // 当滑到最后一个继续上拉, 无法显示 viewpager 的水波纹效果
    // 好在自带 Refresh 有一个 enabled 属性, 在 viewpager 不处于顶端时禁用
    // 这样就可以抵消冲突, 在最后一个上拉时显示水波纹效果
    // 0:不需要下拉刷新, 1:本身就不需要水波纹, 2:需要下拉刷新+水波纹
    const useRefresh = !horizontal && refreshControl ? (
      disableWave ? 1 : 2
    ) : 0;

    // 设置需要监听的事件 
    // 1. 为了可以同步获取当前 index, 默认监听 onPageChanged
    // 2. 如果 autoplay, 默认监听 onPageScrollStateChanged
    const listeners = {};
    eventListenrs.forEach(k => {
      if (k in leftProps && leftProps[k]) {
        listeners[k] = true
      }
    });
    if (!listeners.onPageChanged) {
      listeners.onPageChanged = true;
    }
    if (!listeners.onPageScrollStateChanged && autoplay) {
      listeners.onPageScrollStateChanged = true;
    }

    // 是否使用 item 背景
    let withBackgroundView = false;
    if (this._backgroundView) {
      withBackgroundView = true;
      const background = <View style={this._itemStyle} collapsable={false} key="itemBackground">
        {this._backgroundView}
      </View>;
      if (extraProps.itemIsChild) {
        extraProps.children.unshift(background);
      } else {
        extraProps.children = [background].concat(extraProps.children)
      }
    }

    const props = {
      ...leftProps,
      ...extraProps,
      ref:"pager",
      style,
      listeners,
      horizontal,
      disableWave,
      disableSwipe,
      withBackgroundView,
      currentIndex: this._getShowIndex(currentIndex),
      transformer: makeTransformer(transformer, leftProps),
      onViewpager2Event: this._onViewpager2Event.bind(this),
    };
    if (useRefresh === 0) {
      return <RNViewpager2 {...props} />
    }
    const {outer, inner} = splitLayoutProps(StyleSheet.flatten(props.style));
    props.style = [styles.baseVertical, inner];
    const refreshProps = {
      refreshControl,
      style:[styles.baseVertical, outer]
    };
    if (useRefresh > 1) {
      refreshProps.ref = r => this._refreshRef=r
    }
    return <PagerRefresh {...refreshProps}><RNViewpager2 {...props} /></PagerRefresh>
  }
}

const styles = StyleSheet.create({
  baseVertical: {
    flexGrow: 1,
    flexShrink: 1,
    flexDirection: 'column',
    overflow: 'scroll',
  }
});