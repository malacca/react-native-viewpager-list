# 安装

`yarn add react-native-viewpager2`

（当前仅支持 android，安装完成后无需任何配置）

# 使用

```js
import {ViewPager, ViewPagerList} from 'react-native-viewpager2';


<ViewPager {...props}>
    <View />
    <View />
    <View />
</ViewPager>


<ViewPagerList {...props}/>
```

支持 `ViewPager` 和 `ViewPagerList` 两种方式；前者较为方便，有多少个子 view 就有多少个 page；后者主要使用在有很多 page 的情况，比如类似抖音那样，无限下滑。

## 共同支持的 props

```js
<ViewPager 
   disableSwipe={false}  //禁用滑动(仍可通过API滑动，但用户不可操作)
   currentItem={0}  //当前显示 page
   horizontal={false}  //是否为横向(默认为纵向)
   loop={false}      //是否循环播放
   offscreenPageLimit={0}  //离屏(预加载) page 个数, 
                           //即当前 page 的前后提前渲染的 page 个数
                           //对于 ViewPagerList 创建后不可不可修改, ViewPager 无此限制

   transformer="|card|zoomOut|depth"   //页面过渡效果,默认为空
   scale={0.85}     //过渡效果: 渐隐的最小缩放比例
   alpha={0.75}     //过渡效果: 渐隐的最小透明度 (depth效果不支持)
   padding={int}  // card 过渡效果: 一屏显示多个, padding 设置 主 page 两端间距
   maring={int}   // margin 设置两端(前后) page 的显示尺寸
   
   //当前显示 page 的背景组件
   //利用该属性可实现类似抖音效果, 背景组件可设置为一个 Video 组件
   //这也可避免滑动过程中更新 子view 导致视频组件不断的销毁与重建
   //当然也可在其他性能敏感的场景使用
   getBackground={ReactComponent|Function} 

    // 下拉刷新, 与 ScrollView 使用方式一致, 仅支持垂直视图
    refreshControl={
        <RefreshControl
        refreshing={this.state.refreshing}
        onRefresh={this._onRefresh}
        />
    }

    //page状态发生改变,在滑动开始/滑动松开/滑动完成等节点触发
    onPageScrollStateChanged={({state}) => {}}  
    //滑动过程的回调
    onPageScroll={({position, offset, offsetPixels}) => {}}
    //页面切换完成后触发
    onPageSelected={({position}) => {}}
/>
```

## `ViewPagerList` 专用

`ViewPagerList` 的 page 不支持直接使用子 view，而是使用类似 `FlatList` 的方式，由 数据 和 渲染函数动态创建 page，对于数据较多的情况，可大幅提升性能。

```js
<ViewPagerList
    initData={Array}  //绑定数据
    renderItem={Function}  //page渲染函数

    ref="pager"  //一般需要这个, 下面会解释
/>
```

### `renderItem`

创建第 index 个 page 的 view

```js
renderItem = (item, index) => {
    return <View>{item.balbla}</View>
}
```

### `update` / `push` / `updateItem` / `renderItem`

`initData` 属性仅在创建时有效, 后续更新无效, 若更新数据, 必须使用这三个函数，需要给 `ViewPagerList` 设置 `ref` 属性，之后就可以调用 API 了

```js
// 一次性更新所有数据, 可放心使用, 会自动按需更新
this.refs.pager.update(Array)

// 追加一批数据
this.refs.pager.push(Array)

// 更新指定 page 的数据
this.refs.pager.updateItem(index, data);

// 强制更新指定 page 的视图, 会触发 renderItem
this.refs.pager.renderItem(index);
```

## 其他 API

以下 api 属于公用接口

### `setCurrentItem`

```js
// 滑动到指定页面, smooth=true, 使用过渡效果, 反之不适用
this.refs.pager.setCurrentItem(index, smooth);
```

### `getCurrentItem`

```js
// 获取当前显示的 page index
// 这是一个异步函数, 若需要同步, 可在 onPageSelected 回调中自行缓存
this.refs.pager.getCurrentItem().then(index => {})
```

### `beginFakeDrag` / `fakeDragBy` / `endFakeDrag`

```js
// 模拟手势, 暂时没想到使用场景, 但 viewpager2 提供了, 索性也暴露给 js
// 需注意: beginFakeDrag 和 endFakeDrag 必须成对出现, 否则会闪退
const pager = this.refs.pager;
pager.beginFakeDrag();
pager.fakeDragBy(30);
pager.endFakeDrag();
```