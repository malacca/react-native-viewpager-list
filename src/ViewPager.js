import React from 'react';
import ViewPagerBase from './ViewPagerBase';

class ViewPager extends ViewPagerBase {

  // 首次挂载, 需命令通知 currentItem, 因为首次传递 props.currentItem 时
  // native 端 viewpager 的子 view 个数为 0, currentItem 会被忽略
  componentDidMount(){
    this._setNativeShowItem(
      this._getShowItem(this.props.currentItem)
    )
  }

  componentDidUpdate(prevProps){
    if (Boolean(this.props.loop) !== Boolean(prevProps.loop)) {
      this._startAutoPlay(true);
    }
  }

  _renderSubViews(children){
    if (!children.length) {
      return [];
    }
    const itemStyle = this._itemStyle;
    const newChildren = React.Children.map(children, function(child, index) {
      if (!child) {
        return null;
      }
      const newProps = {
        ...child.props,
        style: [child.props.style, itemStyle],
        collapsable: false,
        key: "item" + index, // 重置子view key, 避免 key 变化太大导致的重建花销
      };
      return React.createElement(child.type, newProps);
    });
    if (this._isLoop) {
      const first = newChildren[0], last = newChildren[newChildren.length - 1];
      newChildren.unshift(
        React.cloneElement(last, {key:"_loop_first_"})
      );
      newChildren.push(
        React.cloneElement(first, {key:"_loop_last_"})
      );
    }
    return newChildren;
  }
  
  render() {
    const children = this.props.children;
    this._setCount(children.length);
    this._computeBeforeRender();
    return this._renderViewpager({
      itemIsChild: true,
      offscreenPageLimit: this.props.offscreenPageLimit,
      children: this._renderSubViews(children)
    })
  }
}

export default ViewPager;