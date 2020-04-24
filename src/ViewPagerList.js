import React from 'react';
import {View} from 'react-native';
import ViewPagerBase from './ViewPagerBase';

function getInt(v) {
  v = parseInt(v);
  return typeof v === "number" && isFinite(v) && Math.floor(v) === v ? v : null;
}

/**
 * 针对 viewpager item 特别多的情况
 * 可使用这个, 可较大改善内存占用
 */
class ViewPagerList extends ViewPagerBase {
  _isPagerList = true;
  _offscreenPage = null;
  _itemCacheSize = 0;
  _viewPageData = [];
  _recyleViews = null;
  _recyleIndex = {};

  constructor(props) {
    super(props);
    this._viewPageData = props.initData||[];

    // android native 端使用 viewpager2, 离屏 offscreenPageLimit 个数影响到 复用view 的个数
    // 若修改参数导致复用 view 数减少, 会使 native 与 js 配合变得复杂许多
    // 考虑到这种情况很少, 所以干脆不支持 offscreenPageLimit 仅支持首次设置
    const {offscreenPageLimit=-1} = props;
    this._offscreenPage = offscreenPageLimit;

    // 复用view 个数的计算公式为 count = 5 + 2 * offscreenPageLimit + 一屏显示item个数 - 1;
    // 一般情况下, 一屏显示 1 个 item, 但在有些情况(如 transformer=card), 一般显示3个
    // 即实际显示 item 的两端会显示 前后 item 的一部分, 但也不排除通过 padding margin 的设置, 一屏显示更多个
    // _itemCacheSize 默认根据上述公式计算, 且保持只增不减, 支持通过 itemSize 来设置一屏几个
    // 这里的 itemCacheSize 仅针对一般情况, 但实际使用中可能出现预置 view 不够用的情况
    // 在不够用时, js 会接收到 addViewHolder 消息, 此时需增加子 view
    // 若期望一次性够用, 可根据实际场景设置 itemSize 来调节, 在创建时就多弄几个 view
    const {transformer, itemSize} = props;
    const itemSizeAuto = (itemSize ? itemSize : 1) + (transformer === 'card' ? 3 : 0);
    this._itemCacheSize = 5 + 2 * Math.max(0, offscreenPageLimit) + itemSizeAuto - 1;
  }

  _getLoopCount = () => {
    return this._childrenCount + (this._isLoop ? 2 : 0);
  }

  // 首次挂载, 需命令通知 currentItem, 因为首次传递 props.currentItem 时
  // native 端 viewpager 的子 view 个数为 0, currentItem 会被忽略
  componentDidMount(){
    this._sendCommand('setCount', [
      this._getLoopCount(),
      this._getShowItem(this.props.currentItem)
    ]);
  }

  // loop 发生变动, 需修正
  componentDidUpdate(prevProps){
    const loop = Boolean(this.props.loop);
    if (loop !== Boolean(prevProps.loop)) {
      this._stopAutoPlay();
      if (loop) {
        this._sendCommand('insertCount', [0, 1]);
        this._sendCommand('insertCount', [this._childrenCount + 1, 1]);
      } else {
        this._sendCommand('removeCount', [0, 1]);
        this._sendCommand('removeCount', [this._childrenCount, 1]);
      }
      this._startAutoPlay(true);
    }
  }

  // 追加 list 数据
  push = (data, selected) => {
    this.insert(data, this._viewPageData.length, selected);
  }

  // 在 index 位置插入 data (index 本身也会被替换)
  // index 可缺省, 默认为 0
  insert = (data, index, selected) => {
    data=data||[];
    if (data.length > 0) {
      index = index === undefined ? 0 : index;
      const Len = this._viewPageData.length;
      this._viewPageData.splice(index, 0, ...data);
      this._updateCount(this._isLoop || index < Len, true, selected);
    }
  }

  // 从 index 位置开始移除 length 个(含 index)
  // length 可缺省, 默认为 1
  remove = (index, length, selected) => {
    length = length === undefined ? 1 : length;
    if (length > 0) {
      this._viewPageData.splice(index, length);
      this._updateCount(true, true, selected);
    }
  }

  // 一次性更新 list 数据
  update = (listData, selected) => {
    const Len = this._viewPageData.length;
    this._viewPageData = listData||[];
    this._updateCount(true, Len !== this._viewPageData.length, selected);
  }

  // 数据长度发生变化
  _updateCount = (reset, notice, selected) => {
    this._setCount(this._viewPageData.length);
    if (reset) {
      for (let from in this._recyleIndex) {
        this._updateSubView(from, this._recyleIndex[from])
      }
    }
    selected = getInt(selected);
    if (notice) {
      const args = [this._getLoopCount()];
      if (selected !== null) {
        args.push(selected);
      }
      this._sendCommand('setCount', args);
    } else if (selected !== null) {
      this.setCurrentItem(selected, false);
    }
  }

  // 获取当前列表所有数据 或 指定index的数据
  getItem = (index) => {
    index = getInt(index);
    if (index === null) {
      return this._viewPageData;
    }
    return index >=0 && index < this._viewPageData.length ? this._viewPageData[index] : null; 
  }

  // 更新 index 数据并重新 render
  updateItem = (index, data) => {
    this._updateItem(index, false, data);
  }

  // 不更新数据, 仅重新 render
  renderItem = (index) => {
    this._updateItem(index, true);
  }

  _updateItem = (index, force, data) => {
    if (index < 0 || index >= this._childrenCount) {
      return;
    }
    if (!force) {
      this._viewPageData[index] = data;
    }
    let copyIndex = index, k, to;
    if (this._isLoop) {
      if (index === 0) {
        copyIndex = this._childrenCount;
      } else if (index === this._childrenCount - 1) {
        copyIndex = -1;
      }
    }
    for (k in this._recyleIndex) {
      to = this._recyleIndex[k];
      if (this._isLoop) {
        if (to === index || to === copyIndex) {
          this._renderItem(k, index, force);
        }
      } else if (to === index) {
        this._renderItem(k, index, force);
        break;
      }
    }
  }

  _renderItem = (k, index, force) => {
    const key = 'recyle' + k;
    if (force) {
      this.refs[key].forceUpdate();
    } else {
      this.refs[key].update(this._viewPageData[index], index);
    }
  }

  // 处理 native 端消息
  _onViewpager2Event(e) {
    const nativeEvent = e.nativeEvent;
    if (nativeEvent.event === "bindViewHolder") {
      this._updateSubView(
        nativeEvent.from, 
        this._isLoop ? nativeEvent.to - 1 : nativeEvent.to
      );
    } else if (nativeEvent.event === "addViewHolder") {
      this._itemCacheSize++;
      this.forceUpdate();
    } else {
      super._onViewpager2Event(e);
    }
  }

  // 重新渲染指定的 子view 
  _updateSubView(from, to) {
    const key = 'recyle' + from;
    if (!(key in this.refs)) {
      return;
    }
    let index = to;
    if (index < 0) {
      if (!this._isLoop) {
        return;
      }
      index = this._childrenCount - 1;
    } else if (index >= this._childrenCount) {
      if (!this._isLoop) {
        return;
      }
      index = 0;
    }
    this.refs[key].update(this._viewPageData[index], index);
    this._recyleIndex[from] = to;
  }

  // 插入子 view, 这些 view 会重复使用
  _renderSubViews() {
    if (
      this._recyleViews != null 
      && this._recyleViews.length >= this._itemCacheSize
      && !this._itemStyleChange
    ) {
      return this._recyleViews;
    }
    const recyleViews = [];
    for (let key, i=0; i<this._itemCacheSize; i++) {
      key = 'recyle' + i;
      recyleViews.push(<ViewItem key={key} ref={key} index={i} item={null} renderItem={this.props.renderItem} style={this._itemStyle}/>)
    }
    return this._recyleViews = recyleViews;
  }

  render() {
    this._updateCount();
    this._computeBeforeRender();
    return this._renderViewpager({
      offscreenPageLimit: this._offscreenPage,
      children: this._renderSubViews()
    })
  }
}

class ViewItem extends React.PureComponent {
  constructor(props) {
    super(props);
    this.state = {
      item: props.item,
      current: props.index,
    }
  }
  update(item, current){
    this.setState({item, current})
  }
  render(){
    return <View style={this.props.style} collapsable={false}>
      {this.state.item ? this.props.renderItem(this.state.item, this.state.current) : null}
    </View>
  }
}

export default ViewPagerList;