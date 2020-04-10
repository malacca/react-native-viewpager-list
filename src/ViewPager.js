import React from 'react';
import ViewPagerBase from './ViewPagerBase';

class ViewPager extends ViewPagerBase {
  _renderSubViews(children){
    if (!children.length) {
      return [];
    }
    return React.Children.map(children, function(child) {
      if (!child) {
        return null;
      }
      const newProps = {
        ...child.props,
        style: [child.props.style, this._itemStyle],
        collapsable: false,
      };
      return React.createElement(child.type, newProps);
    });
  }
  
  render() {
    this._computeItemStyle();
    return this._renderViewpager({
      itemIsChild: true,
      offscreenPageLimit: this.props.offscreenPageLimit,
      children: this._renderSubViews(this.props.children)
    })
  }
}

export default ViewPager;