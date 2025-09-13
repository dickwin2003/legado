# [English](English.md) [中文](README.md)

[![icon_android](https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/icon_android.png)](https://play.google.com/store/apps/details?id=io.legado.play.release)
<a href="https://jb.gg/OpenSourceSupport" target="_blank">
<img width="24" height="24" src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg?_gl=1*135yekd*_ga*OTY4Mjg4NDYzLjE2Mzk0NTE3MzQ.*_ga_9J976DJZ68*MTY2OTE2MzM5Ny4xMy4wLjE2NjkxNjMzOTcuNjAuMC4w&_ga=2.257292110.451256242.1669085120-968288463.1639451734" alt="idea"/>
</a>

<div align="center">
<img width="125" height="125" src="https://github.com/gedoor/legado/raw/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="legado"/>  
  
Legado / 开源阅读
<br>
<a href="https://gedoor.github.io" target="_blank">gedoor.github.io</a> / <a href="https://www.legado.top/" target="_blank">legado.top</a>
<br>
Legado is a free and open source novel reader for Android.
</div>

[![](https://img.shields.io/badge/-Contents:-696969.svg)](#contents) [![](https://img.shields.io/badge/-Function-F5F5F5.svg)](#Function-主要功能-) [![](https://img.shields.io/badge/-Community-F5F5F5.svg)](#Community-交流社区-) [![](https://img.shields.io/badge/-API-F5F5F5.svg)](#API-) [![](https://img.shields.io/badge/-Other-F5F5F5.svg)](#Other-其他-) [![](https://img.shields.io/badge/-Grateful-F5F5F5.svg)](#Grateful-感谢-) [![](https://img.shields.io/badge/-Interface-F5F5F5.svg)](#Interface-界面-)

>新用户？
>
>软件不提供内容，需要您自己手动添加，例如导入书源等。
>看看 [官方帮助文档](https://www.yuque.com/legado/wiki)，也许里面就有你要的答案。

# Function-主要功能 [![](https://img.shields.io/badge/-Function-F5F5F5.svg)](#Function-主要功能-)
[English](English.md)

<details><summary>中文</summary>
1.自定义书源，自己设置规则，抓取网页数据，规则简单易懂，软件内有规则说明。<br>
2.列表书架，网格书架自由切换。<br>
3.书源规则支持搜索及发现，所有找书看书功能全部自定义，找书更方便。<br>
4.订阅内容,可以订阅想看的任何内容,看你想看<br>
5.支持替换净化，去除广告替换内容很方便。<br>
6.支持本地TXT、EPUB阅读，手动浏览，智能扫描。<br>
7.支持高度自定义阅读界面，切换字体、颜色、背景、行距、段距、加粗、简繁转换等。<br>
8.支持多种翻页模式，覆盖、仿真、滑动、滚动等。<br>
9.软件开源，持续优化，无广告。
</details>

# Architecture-项目架构 [![](https://img.shields.io/badge/-Architecture-F5F5F5.svg)](#Architecture-项目架构-)

## 技术栈
- **开发语言**: Kotlin
- **架构模式**: MVVM + Repository
- **数据库**: Room (SQLite)
- **网络请求**: OkHttp3 + Kotlin协程
- **响应式编程**: Kotlin Flow
- **UI框架**: Android ViewBinding + RecyclerView
- **并发处理**: Kotlin协程 (Coroutines)

## 目录结构
```
app/src/main/java/io/legado/app/
├── data/           # 数据层
│   ├── entities/   # 数据实体 (Room Entity)
│   │   ├── Book.kt           # 书籍实体
│   │   ├── BookSource.kt     # 书源实体
│   │   ├── RssSource.kt      # RSS源实体
│   │   ├── RuleSub.kt        # 订阅规则实体
│   │   └── ...
│   ├── dao/        # 数据访问对象 (Room DAO)
│   │   ├── BookDao.kt        # 书籍数据访问
│   │   ├── BookSourceDao.kt  # 书源数据访问
│   │   ├── RuleSubDao.kt     # 订阅规则数据访问
│   │   └── ...
│   ├── AppDatabase.kt        # 数据库主类
│   └── DatabaseMigrations.kt # 数据库迁移
├── ui/             # 用户界面层
│   ├── main/       # 主界面模块
│   ├── book/       # 书籍相关界面
│   │   ├── read/   # 阅读界面
│   │   ├── search/ # 搜索界面
│   │   └── source/ # 书源管理
│   ├── rss/        # RSS订阅相关
│   │   ├── subscription/ # 订阅管理
│   │   ├── source/       # RSS源管理
│   │   └── read/         # RSS阅读
│   └── association/ # 导入关联功能
├── model/          # 业务逻辑模型
├── help/           # 工具类和助手
│   ├── http/       # 网络请求封装
│   ├── config/     # 配置管理
│   └── storage/    # 存储管理
├── service/        # 后台服务
├── utils/          # 工具类
└── constant/       # 常量定义
```

## 核心组件

### 1. 数据层设计
- **Room数据库**: 使用Room作为SQLite的抽象层，提供类型安全的数据访问
- **实体关系**: Book(书籍) ← BookChapter(章节)，BookSource(书源) → Book(书籍)
- **Flow响应式**: 所有数据查询都返回Flow，支持响应式UI更新

### 2. 网络层架构
- **OkHttpClient**: 统一的HTTP客户端，支持Cookie管理、重试机制
- **协程封装**: 使用`suspend`函数包装网络调用，支持取消和超时
- **请求构建**: 链式调用构建复杂HTTP请求 (GET/POST/Multipart)

### 3. 业务逻辑层
- **Repository模式**: 数据仓库模式，统一数据访问接口
- **ViewModel**: 持有UI状态，处理业务逻辑，生命周期感知
- **UseCase**: 封装具体业务操作，如书源导入、内容解析等

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Community-交流社区 [![](https://img.shields.io/badge/-Community-F5F5F5.svg)](#Community-交流社区-)

## 订阅功能详细实现 [![](https://img.shields.io/badge/-Subscription-FF6B6B.svg)](#订阅功能详细实现-)

### 数据模型
订阅功能基于`RuleSub`实体类实现:
```kotlin
// app/src/main/java/io/legado/app/data/entities/RuleSub.kt
@Entity(tableName = "ruleSubs")
data class RuleSub(
    val id: Long = System.currentTimeMillis(),
    var name: String = "",           // 订阅名称
    var url: String = "",            // 订阅URL
    var type: Int = 0,               // 类型：0=书源，1=RSS源，2=替换规则
    var customOrder: Int = 0,        // 自定义排序
    var autoUpdate: Boolean = false, // 是否自动更新
    var update: Long = System.currentTimeMillis() // 更新时间
)
```

### 核心流程
1. **订阅管理界面**: `RuleSubActivity` 负责订阅的CRUD操作
2. **响应式数据**: 使用Flow监听数据变化，实时更新UI
3. **网络导入**: 支持URL导入、JSON解析、自动更新机制
4. **类型分发**: 根据订阅类型(书源/RSS/替换规则)调用不同的导入对话框

### 关键代码位置
- **实体定义**: `app/src/main/java/io/legado/app/data/entities/RuleSub.kt:7-16`
- **数据访问**: `app/src/main/java/io/legado/app/data/dao/RuleSubDao.kt:10-29`
- **界面实现**: `app/src/main/java/io/legado/app/ui/rss/subscription/RuleSubActivity.kt:35-144`
- **导入逻辑**: `app/src/main/java/io/legado/app/ui/association/ImportBookSourceViewModel.kt:189-206`

### 网络请求机制
```kotlin
// 支持重试、协程、错误处理的HTTP封装
suspend fun OkHttpClient.newCallResponse(
    retry: Int = 0,
    builder: Request.Builder.() -> Unit
): Response {
    for (i in 0..retry) {
        response = newCall(requestBuilder.build()).await()
        if (response.isSuccessful) return response
    }
}
```

## 阅读器核心功能 [![](https://img.shields.io/badge/-Reader-4ECDC4.svg)](#阅读器核心功能-)

### 架构设计
- **BaseReadBookActivity**: 基础阅读功能抽象
- **ReadBookActivity**: 主阅读界面，处理用户交互
- **ReadBookViewModel**: 阅读状态管理，章节加载逻辑
- **ReadView**: 页面渲染引擎，支持多种翻页效果
- **ContentTextView**: 文本内容显示，支持选择、搜索等

### 核心特性
- **多格式支持**: TXT, EPUB, MOBI等本地格式
- **在线解析**: 基于自定义规则解析网络小说
- **翻页动画**: 覆盖、仿真、滑动、滚动等多种模式
- **自定义主题**: 字体、颜色、背景、间距全面可定制

## 书源系统架构 [![](https://img.shields.io/badge/-BookSource-45B7D1.svg)](#书源系统架构-)

### 数据结构
```kotlin
// 书源实体包含完整的网站解析规则
@Entity(tableName = "book_sources")
data class BookSource(
    var bookSourceUrl: String = "",      // 书源URL(主键)
    var bookSourceName: String = "",     // 书源名称
    var bookSourceGroup: String? = null, // 书源分组
    var enabled: Boolean = true,         // 是否启用
    var ruleSearch: BookSourceRule? = null,  // 搜索规则
    var ruleExplore: BookSourceRule? = null, // 发现规则
    var ruleBookInfo: BookSourceRule? = null, // 书籍信息规则
    var ruleToc: BookSourceRule? = null,      // 目录规则
    var ruleContent: BookSourceRule? = null   // 正文规则
)
```

### 解析引擎
- **规则语法**: 支持XPath、JSONPath、CSS选择器、正则表达式
- **JavaScript执行**: 内置Rhino引擎，支持复杂的JavaScript规则
- **网络请求**: 自动处理Cookie、User-Agent、请求头等
- **内容处理**: 支持替换、净化、格式转换等后处理

#### Telegram
[![Telegram-group](https://img.shields.io/badge/Telegram-%E7%BE%A4%E7%BB%84-blue)](https://t.me/yueduguanfang) [![Telegram-channel](https://img.shields.io/badge/Telegram-%E9%A2%91%E9%81%93-blue)](https://t.me/legado_channels)

#### Discord
[![Discord](https://img.shields.io/discord/560731361414086666?color=%235865f2&label=Discord)](https://discord.gg/VtUfRyzRXn)

#### Other
https://www.yuque.com/legado/wiki/community

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# API [![](https://img.shields.io/badge/-API-F5F5F5.svg)](#API-)

## 开发者接口 [![](https://img.shields.io/badge/-Developer_API-96CEB4.svg)](#开发者接口-)

### Web API
阅读3.0 提供了2种方式的API：`Web方式`和`Content Provider方式`。您可以在[这里](api.md)根据需要自行调用。

### URL Scheme
可通过url唤起阅读进行一键导入,url格式: `legado://import/{path}?src={url}`

**支持的路径类型:**
- `bookSource`: 书源导入
- `rssSource`: 订阅源导入  
- `replaceRule`: 替换规则导入
- `textTocRule`: 本地txt小说目录规则
- `httpTTS`: 在线朗读引擎
- `theme`: 主题导入
- `readConfig`: 阅读排版配置
- `dictRule`: 字典规则
- `addToBookshelf`: [添加到书架](/app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt)

### 内部API架构

#### 1. 数据访问层 (DAO)
```kotlin
// 响应式数据访问示例
@Query("select * from ruleSubs order by customOrder")
fun flowAll(): Flow<List<RuleSub>>  // 实时数据流

@Query("select * from books where origin = :sourceUrl")
suspend fun getBooksBySource(sourceUrl: String): List<Book>  // 协程查询
```

#### 2. 网络请求层
```kotlin
// OkHttp + 协程封装
suspend fun OkHttpClient.newCallStrResponse(
    retry: Int = 0,
    builder: Request.Builder.() -> Unit
): StrResponse

// 请求构建器扩展
fun Request.Builder.postJson(json: String?)
fun Request.Builder.postForm(form: Map<String, String>)
fun Request.Builder.addHeaders(headers: Map<String, String>)
```

#### 3. 业务逻辑层
```kotlin
// ViewModel + Repository 模式
class ImportBookSourceViewModel : BaseViewModel() {
    fun importSource(text: String)           // 导入书源
    fun importSelect(finally: () -> Unit)    // 选择性导入
    private suspend fun importSourceUrl(url: String) // URL导入
    private fun comparisonSource()           // 源对比
}
```

## 规则引擎详解 [![](https://img.shields.io/badge/-Rule_Engine-FFB6C1.svg)](#规则引擎详解-)

### 支持的选择器类型
1. **XPath**: `//div[@class='title']/text()`
2. **JSONPath**: `$.data[*].title`  
3. **CSS选择器**: `div.title:nth-child(1)`
4. **正则表达式**: `title="([^"]+)"`
5. **JavaScript**: `<js>document.querySelector('.title').innerText</js>`

### 规则执行流程
```
网页内容 → 规则解析器 → 选择器执行 → 结果处理 → 内容输出
    ↓           ↓           ↓          ↓          ↓
  HTML/JSON   规则分析    元素提取    替换净化    最终内容
```

### 高级功能
- **变量支持**: `{{$.title}}` 引用其他规则结果
- **条件判断**: `<if>condition</if>content<else>alternative</else>`
- **循环处理**: `<for>items</for>template`
- **函数调用**: `@js:function()` 调用自定义JavaScript函数

* 阅读3.0 提供了2种方式的API：`Web方式`和`Content Provider方式`。您可以在[这里](api.md)根据需要自行调用。 
* 可通过url唤起阅读进行一键导入,url格式: legado://import/{path}?src={url}
* path类型: bookSource,rssSource,replaceRule,textTocRule,httpTTS,theme,readConfig,dictRule,[addToBookshelf](/app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt)
* path类型解释: 书源,订阅源,替换规则,本地txt小说目录规则,在线朗读引擎,主题,阅读排版,添加到书架

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Other-其他 [![](https://img.shields.io/badge/-Other-F5F5F5.svg)](#Other-其他-)
##### 免责声明
https://gedoor.github.io/Disclaimer

##### 阅读3.0
* [书源规则](https://mgz0227.github.io/The-tutorial-of-Legado/)
* [更新日志](/app/src/main/assets/updateLog.md)
* [帮助文档](/app/src/main/assets/web/help/md/appHelp.md)
* [web端书架](https://github.com/gedoor/legado_web_bookshelf)
* [web端源编辑](https://github.com/gedoor/legado_web_source_editor)

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Grateful-感谢 [![](https://img.shields.io/badge/-Grateful-F5F5F5.svg)](#Grateful-感谢-)
> * org.jsoup:jsoup
> * cn.wanghaomiao:JsoupXpath
> * com.jayway.jsonpath:json-path
> * com.github.gedoor:rhino-android
> * com.squareup.okhttp3:okhttp
> * com.github.bumptech.glide:glide
> * org.nanohttpd:nanohttpd
> * org.nanohttpd:nanohttpd-websocket
> * cn.bingoogolapple:bga-qrcode-zxing
> * com.jaredrummler:colorpicker
> * org.apache.commons:commons-text
> * io.noties.markwon:core
> * io.noties.markwon:image-glide
> * com.hankcs:hanlp
> * com.positiondev.epublib:epublib-core
<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Interface-界面 [![](https://img.shields.io/badge/-Interface-F5F5F5.svg)](#Interface-界面-)
<img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B1.jpg" width="270"><img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B2.jpg" width="270"><img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B3.jpg" width="270">
<img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B4.jpg" width="270"><img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B5.jpg" width="270"><img src="https://github.com/gedoor/gedoor.github.io/blob/master/static/img/legado/%E9%98%85%E8%AF%BB%E7%AE%80%E4%BB%8B6.jpg" width="270">

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>
#   l e g a d o  
 