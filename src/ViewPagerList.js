import React from 'react';
import {View} from 'react-native';
import ViewPagerBase from './ViewPagerBase';

/**
 * 针对 viewpager item 特别多的情况
 * 可使用这个, 可较大改善内存占用
 */
class ViewPagerList extends ViewPagerBase {
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
    const {transformer, itemSize} = props;
    const itemSizeAuto = transformer === 'card' ? (itemSize ? itemSize : 3) : 1;
    this._itemCacheSize = 5 + 2 * Math.max(0, offscreenPageLimit) + itemSizeAuto - 1;
  }

  // 更新 list 数据
  update = (listData) => {
    listData = listData||[];
    const countChange = listData.length !== this._viewPageData.length;
    this._viewPageData = listData;
    for (let from in this._recyleIndex) {
      this._updateSubView(from, this._recyleIndex[from])
    }
    if (countChange) {
      this._sendCommand('setCount', [listData.length]);
    }
  }

  // 更新 index 数据并重新 render
  updateItem = (index, data) => {
    this._upItem(index, true, data);
  }

  // 不更新数据, 仅重新 render
  renderItem = (index) => {
    this._upItem(index, true);
  }

  _upItem = (index, force, data) => {
    if (index >= this._viewPageData.length) {
      return;
    }
    if (!force) {
      this._viewPageData[index] = data;
    }
    for (let k in this._recyleIndex) {
      if (this._recyleIndex[k] === index) {
        const key = 'recyle' + k;
        if (force) {
          this.refs[key].forceUpdate();
        } else {
          this.refs[key].update(this._viewPageData[index], index);
        }
        break;
      }
    }
  }

  // 首次挂载, 需再次告知 currentItem, 因为首次传递 props.currentItem 时
  // viewpager 的子 view 为 0, 会被 native 端忽略
  componentDidMount(){
    const {currentItem=0} = this.props;
    this._sendCommand('setCount', [this._viewPageData.length, currentItem])
  }

  // 处理 native 端消息
  _onViewpager2Event(e) {
    const nativeEvent = e.nativeEvent;
    if (nativeEvent.event === "bindViewHolder") {
      this._updateSubView(nativeEvent.from, nativeEvent.to);
    } else if (nativeEvent.event === "addViewHolder") {
      this._itemCacheSize++;
      this.forceUpdate();
    } else {
      super._onViewpager2Event(e);
    }
  }

  // 重新渲染子 view 
  _updateSubView(from, to) {
    const key = 'recyle' + from;
    if (key in this.refs && to < this._viewPageData.length) {
      this.refs[key].update(this._viewPageData[to], to);
      this._recyleIndex[from] = to;
    }
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
    this._computeItemStyle();
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