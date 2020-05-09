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

# 通用 props

```js
<ViewPagerList
   disableSwipe={false}  //禁用滑动(仍可通过API滑动，但用户不可操作)
   disableWave={false} //是否禁用 滑动到边缘时继续拖拽的水波纹效果
   currentIndex={0}  //当前显示 page
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

### `refreshControl` 说明

与 ScrollView 不同之处在于，不需要设置 `refreshing` 属性，若不使用 RN 自带的 `refreshControl` 刷新器，需保证自行使用的刷新器至少要支持 `refreshing`、`enabled`、`onRefresh` 三个属性（若不支持的话，可考虑套一层 wrapper）。回调函数会有一个函数参数，完成数据更新后，只需要调用一下即可，使用方法如下

```js
_onRefresh = (resolve) => {
    setTimeout(() => {
        resolve()
    }, 3000);
}
```

# 通用 API

以下 api 属于共用接口，`ViewPager` 和 `ViewPagerList` 都支持

### `getCount`

```js
// 获取当前页面数量
int count = this.refs.pager.getCount();
```

### `setCurrentIndex`

```js
// 滑动到指定页面, smooth=true, 使用过渡效果, 反之不使用
this.refs.pager.setCurrentIndex(index, smooth);
```

### `getCurrentIndex`

```js
// 获取当前正在显示的 page index
int index = this.refs.pager.getCurrentIndex()
```

### `disable`

```js
// 禁用或启用当前 viewpager, 禁用后仍可通过 api 切换页面
// 但用户不可拖拽操作了, 与直接修改 props.disableSwipe 效果相同
// 不过性能更好一点
this.refs.pager.disable(disabled)
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

# `ViewPagerList` 专用

`ViewPagerList` 的 page 不支持直接使用子 view，而是使用类似 `FlatList` 的方式，由 数据 和 渲染函数动态创建 page，对于数据较多的情况，可大幅提升性能。

```js
<ViewPagerList
    initData={Array}  //绑定数据
    renderItem={Function}  //page渲染函数

    ref="pager"
/>
```

### `renderItem`

```js
// 创建第 index 个 page 的 view
// item 为 initData 中下标为 index 的数据
renderItem = (item, index) => {
    return <View>{item.balbla}</View>
}
```

修改数据可以直接设置 `props.initData`，但 `ViewPagerList` 的设计初衷是出于性能考虑，所以不建议直接设置，而是根据实际情况使用以下 API 来操作数据

```js
const pager = this.refs.pager;

// 更新列表数据
// 以下四个函数可额外指定一个 selected 参数
// 在更新完数据后, 将 viewpager 定位到指定页面
// 可缺省, 即不改变当前的 selected page
//---------------------------------------------------

// 追加一批数据
pager.push(Array, selected)

// 在 index 位置插入 data (index 本身也会被替换)
// index 可缺省, 默认为 0, 即插入到开头
pager.insert(Array, index, selected)

// 从 index 位置开始移除 length 个(含 index)
// length 可缺省, 默认为 1
pager.remove(Array, index, selected)

// 一次性更新所有数据, 可放心使用, 会自动按需更新
pager.update(Array, selected)


// 获取/更新 指定 page 的数据
//---------------------------------------------------

// 获取所有 page 数据
Array list = pager.getItem();

// 获取下标 index 的 page 数据
Object item = pager.getItem(index);

// 更新指定 page 的数据
pager.updateItem(index, data);

// 强制更新指定 page 的视图(即使其 data 未变动), 会触发 renderItem
pager.renderItem(index);


// 针对当前正在显示 page 的操作
//---------------------------------------------------

// 获取当前 page 的数据
// 等价于 pager.getItem(this.getCurrentIndex())
Object item = pager.getCurrentItem();

// 更新当前 page 的数据
// 等价于 pager.updateItem(this.getCurrentIndex(), data)
pager.updateCurrentItem(data);

```

### API 使用注意事项

```js
// getItem / getCurrentItem 获取到的数据为原始数据, 并非拷贝的数据
const item = this.refs.pager.getCurrentItem();

// 不会触发 renderItem 重新渲染 page, 但在 page 回收后再次重新创建时,
// 会触发 renderItem 重新渲染, 回收时机由 offscreenPageLimit 决定
item.foo = "foo";

// 同上, 此时仍不会触发, 相当于设置了 item=item, 无法触发 RN 的 props diff 
item.foo = "foo";
this.refs.pager.updateItem(
    this.refs.pager.getCurrentIndex(),
    item
);

// 以下方式可立即更新
this.refs.pager.updateItem(
    this.refs.pager.getCurrentIndex(),
    {...item, foo:"foo"}
);

// 这样设计的原因是为了应对以下场景
// 1. 已通过其他方式更新完了 page, 仅需改变数据以便下次正确渲染
// 2. 没更新 page, 通过改变数据触发 renderItem 立即重新渲染
```


# 额外说明

Page 内部也有可能也有 touchmove 相关的组件, 所以 viewpager 默认并未阻止该类型事件，但这会给以下使用场景造成困扰

```js
import React from 'react';
import {View, TouchableOpacity} from 'react-native';
import {ViewPager} from 'react-native-viewpager-list';

class App extends React.Component {
  render(){
    return <ViewPager>
        <View>
            <TouchableOpacity onPress={() => {}}/>
        </View>
    </ViewPager>
  }  
}
```

按住 `TouchableOpacity` 拖拽切换 `ViewPager` 到下一个页面，此时会触发 `onPress` 事件，这并不符合期望；实际所需要的是点击 `TouchableOpacity` 且不发生页面切换，才响应  `onPress` 事件，可通过以下方式解决。


```js
class App extends React.Component {
  
  _pageState = 0;
  _onPageStateChange = ({state}) => {
      this._pageState = state;
  }  

  // _pageState !== 0 意味着发生了拖拽, 此时阻止子视图响应 touch 事件
  _onMoveShouldSetResponderCapture = () => {
      return this._pageState !== 0;
  }
 
  render(){
    return <ViewPager
        onPageScrollStateChanged={this._onPageStateChange}
        onMoveShouldSetResponderCapture={this._onMoveShouldSetResponderCapture}
    >
        <View>
            <TouchableOpacity onPress={() => {}}/>
        </View>
    </ViewPager>
  }  
}
```