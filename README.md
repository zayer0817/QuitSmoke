# 抽了吗 - Android 戒烟桌面小组件

[![Build & Release APK](https://github.com/zayer0817/QuitSmoke/actions/workflows/build.yml/badge.svg)](https://github.com/zayer0817/QuitSmoke/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/zayer0817/QuitSmoke?include_prereleases&label=download)](https://github.com/zayer0817/QuitSmoke/releases)

> 极简记录，数据说话，统计你的抽烟日常，至于戒烟？随他去吧我们都只活一次。

---

## 版本历史

### v1.6.0 — 自动补录提醒

基于用户抽烟习惯，按时段自动检查今日记录。不足时弹窗提醒一键补录，防止漏记。

| 改进 | 说明 |
|------|------|
| 时段自动检查 | 早间(10:00)/午间(15:00)/晚间(21:00)三时段 + 全天兜底(23:30)，按预期数量检查 |
| 一键补录 | 通知栏提醒 → 点入弹窗 → "补上N根"自动在时段内均匀插入记录 |
| 三种操作 | 补上N根 / 今天抽得少（今日免打扰）/ 稍后提醒（30分钟后） |
| 可配置 | 设置页可调整各时段预期数量（±按钮）和检查时间（点击时间弹出选择器） |
| 功能开关 | 设置页总开关，关闭后完全不提醒 |
| 防骚扰 | 同一天同一时段补录或跳过后不再弹窗；每日凌晨自动重置 |
| 闹钟可靠性 | 新增 BOOT_COMPLETED / MY_PACKAGE_REPLACED 广播，手机重启后自动恢复调度 |
| 主题色跟随 | 补录区域的开关、按钮、时间文字全部跟随主题色 |

### v1.5.1 — 主题色系统 + 小组件重构

主题色全局应用 + 小组件布局重做 + 柱状图颜色实时刷新

| 改进 | 说明 |
|------|------|
| 主题色系统 | 新增 6 种预设颜色（绿/蓝/紫/橙/粉/青）+ 自定义取色器，lightenColor/darkenColor 派生三档变体 |
| 小组件按钮跟随主题 | 桌面小组件按钮颜色跟随应用内主题色，保持视觉一致 |
| 小组件布局重做 | 精简线性布局，数字靠左、按钮靠右，间距均匀，曲率比例协调 |
| Toolbar 导航 | 所有页面改用 MaterialToolbar 替代独立返回按钮 |
| 随机提示语 | 今日提示和今日提醒从 65 条中随机显示 |
| 颜色刷新修复 | 主题色变更后柱状图和小组件按钮即时刷新 |
| 柱状图颜色递进 | 抽得越多柱子颜色越深，视觉上渐进警示 |

---

### v1.5.0 — Material Design 3 标准化

全面采用 Material Design 3 组件规范 + 状态栏统一处理 + 随机提示词 + 连续记录优化 + 长按删除

| 改进 | 说明 |
|------|------|
| MD3组件 | MaterialCardView（Filled Card 无阴影）、MaterialButton、MaterialToolbar |
| 状态栏统一 | 所有页面 fitsSystemWindows + MaterialToolbar，不再与状态栏冲突 |
| 随机提示词 | 今日提示和今日提醒各场景5条随机显示，共65条 |
| 点击响应优化 | 小组件点击后立即显示Toast，数据库写入异步执行 |
| 连续点击间隔 | 3秒内连续点击自动间隔15分钟记录 |
| 长按删除 | 每日详情页长按记录可删除，带确认对话框 |

---

### v1.4.1 — 首页精简

布局重组 + 提示文案精简 + 状态栏沉浸优化

| 改进 | 说明 |
|------|------|
| 布局重组 | 7天趋势图上移与本周统计合并，目标追踪下移，减少滚动 |
| 卡片合并 | 趋势图卡片内嵌本周总览（总/均/趋势），删除独立概览卡片 |
| 移除激励卡 | 删除"你知道吗"卡片，首页更紧凑 |
| 提示精简 | "今日提醒"改为一句短提示，更直接有用 |
| 状态栏可控 | 首页透明沉浸，其他页面用背景色，通过 `useTransparentStatusBar` 控制 |
| 按钮固定宽 | 操作按钮改为固定宽度，避免不同文字长度导致跳动 |

---

### v1.4.0 — 现代化重构

ViewBinding + ViewModel + DataStore + 字符串资源化 + 单元测试

| 改进 | 说明 |
|------|------|
| ViewBinding | 全面替换 `findViewById`，类型安全，消除空指针风险 |
| ViewModel | 主界面引入 `MainViewModel` + `StateFlow`，正确处理配置变更 |
| DataStore | `SharedPreferences` 迁移到 `DataStore`，类型安全且支持协程 |
| 字符串资源化 | 所有硬编码中文移入 `strings.xml`，支持未来本地化 |
| 颜色资源化 | Kotlin 中的硬编码颜色值改为引用颜色资源 |
| 依赖清理 | 移除未使用的 `constraintlayout` 和 `work-runtime-ktx` |
| 单元测试 | 新增 16 个 `SmokeRepository` 测试用例，覆盖核心业务逻辑 |
| Room Schema | 开启 `exportSchema`，支持数据库迁移自动验证 |
| 浅色模式修复 | 修复 ViewBinding 迁移后状态栏图标颜色未正确设置的问题 |
| 偏好迁移 | 从旧版 `SharedPreferences` 自动迁移主题和每日目标，升级后不丢设置 |
| 统计观察期 | 连续无烟、连续达标和周报只从开始使用/最早记录日计算，不再追溯到使用前 |
| 小组件跨天刷新 | 小组件会在次日凌晨自动刷新，避免早上仍显示昨天计数 |

---

### v1.3.1 — 稳定性与构建优化

深色模式适配 + 统计查询优化 + Room/KSP 构建链整理

| 改进 | 说明 |
|------|------|
| 深色模式状态栏 | 状态栏和导航栏图标颜色根据当前主题自动切换 |
| 统计查询优化 | 周报和连续天数统计改为批量查询，减少重复 SQL 调用 |
| 数据库迁移策略 | 保留显式 Migration 要求，移除缺失迁移时清空数据的 destructive fallback |
| Room 编译优化 | Room 注解处理从 kapt 迁移到 KSP |
| 应用初始化 | 新增 `QuitSmokeApp`，集中执行全局主题初始化 |
| 小组件异步处理 | 小组件和操作广播的异步任务使用独立协程作用域 |
| 默认时间修正 | `hourOfDay` 默认值改为 `-1`，避免未设置时间被误判为午夜 |
| 混淆规则补全 | 补充 Room、协程、Widget、Material、ViewBinding 等 ProGuard 规则 |

---

### v1.0.0 — 初始版本

桌面小组件 + 统计分析 + 历史记录 + 撤销操作

| 功能 | 说明 |
|------|------|
| 桌面小组件 | 4×1 尺寸，一个"🚬 抽一根"按钮，一键记录 |
| 实时计数 | 小组件显示今日抽烟次数，动态变色（绿→黄→橙→红） |
| 今日统计 | 主页实时显示当日计数 + 颜色等级提示 |
| 7天趋势图 | 纯原生柱状图，最近一周走势一目了然 |
| 本周概览 | 总计、日均、趋势对比（与上周对比） |
| 时段分析 | 识别抽烟高峰时段 |
| 戒烟建议 | 根据数据给出个性化建议 + 替代方案 |
| 健康激励 | 随机展示戒烟恢复时间线知识 |
| 撤销操作 | 误按可撤销，仅限当天 |
| 历史记录 | 按日期查看所有记录 |
| 小米澎湃适配 | 圆角卡片（24dp）、深色主题、半透明背景 |

---

### v1.1.0 — 功能扩展

手动补录 + 每日详情 + 页面导航优化

| 功能 | 说明 |
|------|------|
| 手动补录 | 新增「添加记录」页面，支持选择日期和时间补录漏记的记录 |
| 每日详情 | 新增「每日详情」页面，查看某天每条记录的具体时间（HH:mm:ss） |
| 图表点击 | 7天趋势图每个柱子可点击，跳转到对应日期的详情页 |
| 历史点击 | 历史记录列表每行可点击，跳转到对应日期的详情页 |
| 返回按钮 | 设置、历史、详情、补录页面均添加"←"返回按钮 |
| 入口调整 | 首页"历史记录"按钮改为"手动添加"，历史记录入口移至设置页 |

---

### v1.2.0 — 沉浸式状态栏

沉浸式体验 + 状态栏适配 + 小组件透明度 + 代码重构

| 功能 | 说明 |
|------|------|
| 沉浸式状态栏 | 状态栏透明，内容延伸至状态栏区域 |
| 首页真沉浸 | 首页内容可滑入状态栏下方，滚动体验流畅自然 |
| 状态栏图标适配 | 浅色背景自动使用深色图标，解决白底白字不可见 |
| 其他页面安全区 | 设置/历史/详情/补录页面标题栏正确避开状态栏 |
| 小组件透明度 | 背景透明度提升至 40%，更好地融入桌面壁纸 |
| BaseActivity 基类 | 统一沉浸式状态栏逻辑，消除多页面重复代码 |

---

### v1.2.1 — 细节修复

编译 warning 清理 + 兼容性修正

| 修复 | 说明 |
|------|------|
| WindowInsetsController 兼容 | 使用 `WindowCompat.getInsetsController()` 替代原生 API，解决低版本编译报错 |
| 空安全修正 | `SimpleDateFormat.parse()` 返回值加非空断言 |
| 未使用参数 | `updateAdvice`/`updateMotivation` 参数加 `@Suppress` 注解 |
| 废弃 API | `startActivityForResult` 加 `@Suppress("DEPRECATION")` |
| 未使用变量 | `SmokeWidgetProvider` 中移除未使用的 `tipColor` |
| 非空调用 | `WindowInsetsControllerCompat` 去掉多余的安全调用符 `?.` |

---

### v1.2.2 — 数据管理与安全加固

导入导出体验优化 + 小组件安全加固 + 版本信息自动同步

| 改进 | 说明 |
|------|------|
| 导入导出 API 更新 | 使用 Activity Result API 替代 `startActivityForResult` |
| CSV 兼容性增强 | 导入/导出支持逗号、双引号和换行内容 |
| 重复导入防护 | 导入备份时自动跳过已存在的相同记录，避免数据翻倍 |
| 导入结果提示 | 导入完成后显示新增、重复、无效记录数量 |
| 小组件安全加固 | 小组件操作广播限制在应用内部，减少外部误触发风险 |
| 小组件刷新补全 | 手动补录和导入数据后会主动刷新桌面小组件 |
| 版本信息同步 | 设置页关于信息自动读取 `BuildConfig.VERSION_NAME` |
| BuildConfig 显式启用 | Gradle 中显式开启 `buildConfig`，保证版本信息可用 |

---

### v1.3.0 — 目标追踪

每日目标 + 连续达标 + 月度统计 + 导入确认 + 触发原因

| 功能 | 说明 |
|------|------|
| 每日上限 | 设置页可调整每日目标根数，首页和小组件按目标给出反馈 |
| 目标追踪 | 首页显示今日进度、连续达标天数、连续无烟天数 |
| 月度统计 | 首页新增本月总数、日均、达标天数、无烟天数 |
| 导入确认 | CSV 导入前预览新增、重复、无效记录数量，确认后再写入 |
| 历史空状态 | 没有历史记录时显示友好提示 |
| 触发原因 | 手动补录可选择饭后、压力、社交、无聊等触发原因 |
| 小组件稳定性 | 小组件广播使用 `goAsync()` 等待异步写库完成 |

---

## 核心功能

### 🎯 桌面小组件
- **4×1 尺寸**，适配小米澎湃系统卡片风格
- 只有一个 **"🚬 抽一根"** 按钮，一键记录
- 实时显示今日已抽烟次数
- 根据数量动态变色提示（绿→黄→橙→红）
- 点击计数区域可进入主应用查看详细统计

### 📊 统计分析
- **今日统计**：实时显示今日抽烟次数，配有颜色等级提示
- **近期趋势**：柱状图 + 本周总览（总计/日均/趋势），点击柱子查看当日详情
- **时段分析**：识别你的抽烟高峰时段

### ✏️ 手动补录
- 支持选择日期和时间，补录之前漏记的抽烟记录
- 日期选择器 + 时间选择器，操作简单直观

### ⏰ 自动补录提醒
- 根据你的抽烟习惯，按时段自动检查今日记录数量
- 早间(10:00) / 午间(15:00) / 晚间(21:00) / 全天兜底(23:30)
- 记录不足时弹窗提醒，一键补上缺失的根数
- 今天抽得少可跳过，当天不再打扰
- 在设置页可调整各时段预期数量和检查时间

### 📅 每日详情
- 点击趋势图柱子或历史记录进入
- 展示当天每条抽烟记录的具体时间（时:分:秒）
- **长按记录可删除**，带确认提示

### 💡 今日提醒
- 根据当前数据给出一句简短提示
- 超标、趋势上升、高峰时段各有针对性提醒

### ↩️ 撤销操作
- 误按可撤销最近一次记录
- 只能撤销今天的记录

### 💾 数据管理
- 在设置页导出 CSV 备份
- 从 CSV 文件导入恢复记录
- 重复导入同一个备份时会自动跳过已有记录
- 导入后显示新增、重复、无效记录数量

### 🎯 目标追踪
- 设置每日抽烟上限
- 首页显示今日目标进度、连续达标、连续无烟
- 本月总数、日均、达标天数和无烟天数一屏可见

---

## 项目结构

```
QuitSmoke/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/quitsmoke/app/
│       │   ├── BaseActivity.kt              # Activity基类（沉浸式状态栏）
│       │   ├── AppPreferences.kt            # DataStore偏好设置（主题+目标）
│       │   ├── MainViewModel.kt             # 主界面ViewModel
│       │   ├── QuitSmokeApp.kt              # Application类（全局初始化）
│       │   ├── MainActivity.kt             # 主页面（统计分析）
│       │   ├── HistoryActivity.kt          # 历史记录页面
│       │   ├── HistoryAdapter.kt           # 历史记录适配器
│       │   ├── DayDetailActivity.kt        # 每日详情页面
│       │   ├── DayDetailAdapter.kt         # 每日详情适配器
│       │   ├── AddRecordActivity.kt        # 手动补录页面
│       │   ├── SettingsActivity.kt         # 设置页面
│       │   ├── data/
│       │   │   ├── SmokeRecord.kt          # 数据实体
│       │   │   ├── SmokeRecordDao.kt       # 数据访问接口
│       │   │   ├── AppDatabase.kt          # Room数据库
│       │   │   └── SmokeRepository.kt      # 数据仓库
│       │   ├── reminder/
│       │   │   ├── ReminderReceiver.kt      # 补录提醒闹钟接收器
│       │   │   └── ReminderActivity.kt      # 补录提醒弹窗
│       │   └── widget/
│       │       ├── SmokeWidgetProvider.kt   # 小组件Provider
│       │       └── SmokeActionReceiver.kt   # 小组件操作接收器
│       └── res/
│           ├── layout/
│           │   ├── widget_smoke.xml          # 小组件布局
│           │   ├── widget_smoke_preview.xml  # 小组件预览布局
│           │   ├── activity_main.xml          # 主页面布局
│           │   ├── activity_history.xml       # 历史记录布局
│           │   ├── activity_day_detail.xml    # 每日详情布局
│           │   ├── activity_add_record.xml    # 手动补录布局
│           │   ├── activity_settings.xml      # 设置页面布局
│           │   ├── item_bar.xml              # 趋势图柱子布局
│           │   ├── item_history.xml           # 历史记录项布局
│           │   └── item_day_detail.xml        # 每日详情项布局
│           ├── drawable/
│           │   ├── widget_background.xml      # 小组件背景
│           │   ├── btn_smoke_bg.xml           # 抽一根按钮背景
│           │   ├── btn_undo_bg.xml            # 撤销按钮背景
│           │   ├── card_background.xml        # 卡片背景
│           │   └── bar_today_bg.xml           # 今日柱子高亮背景
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml
│           └── xml/
│               └── smoke_widget_info.xml     # 小组件配置
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 编译运行

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+（或 Android Studio 自带 JBR）
- Android SDK 34
- Gradle 8.13

### 编译步骤

1. 用 Android Studio 打开项目目录
2. 等待 Gradle Sync 完成
3. 连接手机或启动模拟器
4. 点击 Run 或 `Shift + F10`

### 使用步骤

1. 安装应用到手机
2. **添加桌面小组件**：长按桌面 → 小组件 → 找到"抽了吗" → 拖到桌面
3. 每次抽烟时点击小组件上的"🚬 抽一根"按钮
4. 点击计数区域打开主应用查看统计
5. 忘记录了？点击「手动添加」补录

---

## 设计理念

### 小组件
- **极简主义**：只有一个按钮，降低记录门槛
- **即时反馈**：每次点击后 Toast 提示当日累计数
- **颜色分级**：0根=绿色、1-5=黄色、6-10=橙色、11-20=红色、20+=深红

### 数据分析
- **周报对比**：自动对比本周与上周数据
- **趋势判断**：↑上升（红色）/ ↓下降（绿色）/ →持平
- **时段洞察**：识别最容易抽烟的时间段

### 视觉体验
- **沉浸式状态栏**：首页内容可滑入状态栏下方，滚动体验流畅
- **智能图标适配**：浅色背景自动使用深色状态栏图标
- **半透明小组件**：40% 透明度，融入桌面壁纸

### 小米澎湃适配
- 圆角卡片设计（24dp圆角）
- 深色主题适配
- 半透明背景融合桌面
- 支持 resizeMode 自由调整大小

---

## 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin | 主开发语言 |
| ViewBinding | 类型安全的视图绑定 |
| ViewModel + StateFlow | 状态管理与配置变更处理 |
| DataStore | 现代偏好设置存储 |
| Room + KSP | 本地数据库与注解处理 |
| AppWidgetProvider | 桌面小组件 |
| Coroutines | 异步操作 |
| Material Design | UI组件 |
| Activity Result API | 文件导入导出 |
| JUnit + MockK | 单元测试 |

---

## 常见问题

### 命令行提示 `JAVA_HOME is not set`

如果 Android Studio 能运行项目，但在 PowerShell 里执行 `.\gradlew.bat :app:assembleDebug` 报：

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

说明当前命令行找不到 JDK。项目需要 JDK 17 或 Android Studio 自带的 JBR。

#### 方案一：使用 Android Studio 自带 JDK

1. 打开 Android Studio
2. 进入 `File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Gradle`
3. 查看 `Gradle JDK` 使用的路径
4. 在 Windows 环境变量里新增：

```text
JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
```

如果 Android Studio 安装在 D 盘，也可能是类似：

```text
JAVA_HOME=D:\Android\jbr
```

然后把下面这项加入 `Path`：

```text
%JAVA_HOME%\bin
```

关闭并重新打开 PowerShell，执行：

```powershell
java -version
.\gradlew.bat :app:assembleDebug
```

#### 方案二：安装独立 JDK 17

安装 Temurin JDK 17 或 Oracle JDK 17 后，把 `JAVA_HOME` 指向安装目录，例如：

```text
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x.x.x-hotspot
```

并把 `%JAVA_HOME%\bin` 加入 `Path`。

#### 临时方案：只在当前 PowerShell 生效

如果你只想临时跑一次构建，可以执行：

```powershell
$env:JAVA_HOME="D:\Android\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat :app:assembleDebug
```

路径要替换成你电脑上真实存在的 JDK 目录。

---

## 戒烟小知识

- 戒烟 **20分钟**：心率和血压开始恢复
- 戒烟 **12小时**：血液中一氧化碳水平恢复
- 戒烟 **2-12周**：血液循环改善，肺功能增强
- 戒烟 **1年**：心脏病风险降低一半
- 戒烟 **5年**：中风风险降至非吸烟者水平
- **每少抽一根烟，平均延长11分钟寿命**
