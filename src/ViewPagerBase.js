import React from 'react';
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

const eventListenrs = ['onPageScroll', 'onPageScrollStateChanged', 'onPageSelected'];
export default class extends React.PureComponent {
  _getItemIndex = 0;
  _getItemCallback = {};
  _backgroundView = null;
  _itemStyleStatus = null;
  _itemStyleChange = false;
  _itemStyle = {};
  constructor(props) {
    super(props);
    // 背景 view, 仅初始化时有效, 后续修改会忽略
    const {getBackground:BackgroundComponent} = props;
    if (BackgroundComponent) {
      this._backgroundView = React.isValidElement(BackgroundComponent) ? (
        BackgroundComponent
      ) : (
        <BackgroundComponent />
      );
    }
  }

  // 设置选中的 item
  setCurrentItem = (index, smooth) => {
    this._sendCommand('setCurrentItem', [parseFloat(index), Boolean(smooth)])
  }

  // 获取当前选中的 item index
  getCurrentItem = () => {
    return new Promise(resolve => {
      const key = "index" + (++this._getItemIndex);
      this._getItemCallback[key] = resolve;
      this._sendCommand('getCurrentItem', [key])
    })
  }

  // 开始拖拽(模拟手指按下)
  beginFakeDrag = () => {
    this._sendCommand('beginFakeDrag');
  }

  // 设置模拟拖拽偏移值
  fakeDragBy = (offset) => {
    this._sendCommand('fakeDragBy', [parseInt(offset)]);
  }

  // 结束拖拽(模拟手指松开)
  endFakeDrag = () => {
    this._sendCommand('endFakeDrag');
  }

  // 发送消息给 native 端
  _nodeHandle = null;
  _sendCommand = (command, args) => {
    if (this._nodeHandle === null) {
      this._nodeHandle = findNodeHandle(this.refs.pager);
    }
    UIManager.dispatchViewManagerCommand(this._nodeHandle, command, args);
  }

  // 处理 native 端发送的消息
  _onViewpager2Event(e) {
    const {event, ...msg} = e.nativeEvent;
    if (event === 'getCurrentItem') {
      const {index, item} = msg;
      if (index && index in this._getItemCallback) {
        this._getItemCallback[index](item);
        delete this._getItemCallback[index];
      }
    } else if (eventListenrs.includes(event)) {
      this.props[event] && this.props[event](msg);
    }
  }

  // 计算 item style
  _computeItemStyle() {
    const {horizontal, transformer="", padding=0} = this.props;
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
      refreshControl,
      disableSwipe,
      currentItem,
      horizontal,
      transformer,
      ...leftProps
    } = this.props;
    const listeners = {};
    eventListenrs.forEach(k => {
      if (k in leftProps && leftProps[k]) {
        listeners[k] = true
      }
    });

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
      ref:"pager",
      style,
      disableSwipe,
      currentItem,
      horizontal,
      transformer: makeTransformer(transformer, leftProps),
      listeners,
      onViewpager2Event: this._onViewpager2Event.bind(this),
      withBackgroundView,
      ...extraProps
    };
    if (!refreshControl) {
      return <RNViewpager2 {...props} />
    }
    const {outer, inner} = splitLayoutProps(StyleSheet.flatten(props.style));
    props.style = inner;
    return React.cloneElement(
      refreshControl,
      {style: outer},
      <RNViewpager2 {...props} />
    );
  }
}