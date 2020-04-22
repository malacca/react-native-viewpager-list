# 安装

`yarn add react-native-viewpager-list`

（当前仅支持 android，安装完成后无需任何配置）

# 使用

```js
import {ViewPager, ViewPagerList} from 'react-native-viewpager-list';


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
   disableWave={false} //是否禁用 滑动到边缘时继续拖拽的水波纹效果
   currentItem={0}  //当前显示 page
   horizontal={false}  //是否为横向(默认为纵向)
   loop={false}      //是否循环展示
   offscreenPageLimit={0}  //离屏(预加载) page 个数, 
                           //即当前 page 的前后提前渲染的 page 个数
                           //对于 ViewPagerList 创建后不可不可修改, ViewPager 无此限制

   autoplay={false}  //是否自动播放
   autoplayTimeout={2500}  //自动播放时间间隔
   autoplayDirection={false} //自动播放是否仅沿着一个方向(即播放到结尾后停止)

   transformer="|card|zoomOut|depth"   //页面过渡效果,默认为空
   scale={0.85}     //过渡效果: 渐隐的最小缩放比例
   alpha={0.75}     //过渡效果: 渐隐的最小透明度 (depth效果不支持)
   padding={int}  // card 过渡效果: 一屏显示多个, padding 设置 主 page 两端间距
   maring={int}   // margin 设置两端(前后) page 的显示尺寸
   
   //当前显示 page 的背景组件
   //利用该属性可实现类似抖音效果, 背景组件可设置为一个 Video 组件
   //这也可避免滑动过程中更新 子view 导致视频组件不断的销毁与重建
   //当然也可在其他性能敏感的场景使用
   //该参数仅在首次有效, 创建后更改无效
   getBackground={ReactComponent|Function} 

    // 下拉刷新, 与 ScrollView 使用方式基本一致, 仅支持垂直视图
    refreshControl={
        <RefreshControl
            tintColor=""
            onRefresh={this._onRefresh}
        />
    }

    //page状态发生改变,在滑动开始/滑动松开/滑动完成等节点触发
    onPageScrollStateChanged={({state}) => {}}  
    //滑动过程的回调
    onPageScroll={({position, offset, offsetPixels}) => {}}
    //页面切换后触发(此时是刚松开手指, 即将惯性滑动到 position 页面)
    onPageSelected={({position}) => {}}
    //页面切换完成后触发
    onPageChanged={({position}) => {}}
/>
```

## `refreshControl` 属性

与 ScrollView 不同之处在于，不需要设置 `refreshing` 属性，若不使用 RN 自带的 `refreshControl` 刷新器，需保证自行使用的刷新器至少要支持 `refreshing`、`enabled`、`onRefresh` 三个属性（若不支持的话，可考虑套一层 wrapper）。回调函数会有一个函数参数，完成数据更新后，只需要调用一下即可，使用方法如下

```js
_onRefresh = (resolve) => {
    setTimeout(() => {
        resolve()
    }, 3000);
}
```

## `ViewPagerList` 专用

`ViewPagerList` 的 page 不支持直接使用子 view，而是使用类似 `FlatList` 的方式，由 数据 和 渲染函数动态创建 page，对于数据较多的情况，可大幅提升性能。

```js
<ViewPagerList
    initData={Array}  //绑定数据
    renderItem={Function}  //page渲染函数

    ref="pager"
/>
```

### `renderItem`

创建第 index 个 page 的 view

```js
renderItem = (item, index) => {
    return <View>{item.balbla}</View>
}
```

### `push` / `insert` / `remove` / `update`
### `updateItem` / `renderItem`

`initData` 属性仅在创建时有效, 后续更新无效, 若更新数据, 必须使用以下函数，需要给 `ViewPagerList` 设置 `ref` 属性，之后就可以调用 API 了

```js
// 追加一批数据
this.refs.pager.push(Array, selected)

// 在 index 位置插入 data (index 本身也会被替换)
// index 可缺省, 默认为 0, 即插入到开头
this.refs.pager.insert(Array, index, selected)

// 从 index 位置开始移除 length 个(含 index)
// length 可缺省, 默认为 1
this.refs.pager.remove(Array, index, selected)

// 一次性更新所有数据, 可放心使用, 会自动按需更新
this.refs.pager.update(Array, selected)

// 备注: 以上四个函数可额外指定一个 selected 参数
// 在更新完数据后, 将 viewpager 定位到指定页面, 可缺省
//---------------------------------------------------

// 更新指定 page 的数据
this.refs.pager.updateItem(index, data);

// 强制更新指定 page 的视图(即使其 data 未变动), 会触发 renderItem
this.refs.pager.renderItem(index);
```

## 其他 API

以下 api 属于共用接口，`ViewPager` 和 `ViewPagerList` 都支持

### `getCount`

```js
// 获取当前页面数量
int count = this.refs.pager.getCount();
```

### `setCurrentItem`

```js
// 滑动到指定页面, smooth=true, 使用过渡效果, 反之不使用
this.refs.pager.setCurrentItem(index, smooth);
```

### `getCurrentItem` / `currentItem`

```js
// 获取当前显示的 page index
// 这是一个异步函数, 若需要同步获取, 可在 onPageSelected 回调中自行缓存
this.refs.pager.getCurrentItem().then(index => {})

// 为了省事, 如果是 loop 或 autoplay 或 绑定了 onPageSelected 监听
// 可使用下面函数同步获取当前 page index
int page = this.refs.pager.currentItem()
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