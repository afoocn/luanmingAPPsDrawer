# 峦鸣抽屉 (luanmingAPPsDrawer)

一个把 App 分类收进「悬浮抽屉」的安卓启动器增强工具：通过手势软件的shortcut，从任意界面拉出抽屉。
轻量抽屉 + 集合分组 + 快捷方式pin到桌面 + Icon Pack 总线

## 功能

### 1. 集合管理
- 创建集合，自定义名称和图标颜色
- 集合内可添加任意已安装应用
- 长按集合卡片：编辑 / 发送到桌面 / 删除

### 2. 桌面快捷方式
- 集合可 pin 到系统桌面，生成一个图标
- 点击桌面图标 = 打开该集合
- 支持 Android 11.0+ 的 ShortcutManager 和旧版 INSTALL_SHORTCUT

### 3. 外部调用
其他 APP 可通过 Intent 唤起某个集合：

```kotlin
val intent = Intent("com.fengnian.drawer.LAUNCH_COLLECTION")
intent.putExtra("collection_id", 123L)  // 集合ID
startActivity(intent)
```

### 4. Icon Pack 支持
- 支持标准 Android Icon Pack 协议（ADW Launcher 格式）
- 从已安装的 Icon Pack 主题应用读取替换图标
- 在主界面右上角菜单 → 图标主题 中选择
- 兼容 Neo Icon Pack、Delta Icon Pack 等主流图标包

## 安装

```
adb install luanmingAPPsDrawer-v2.11.apk
```

或直接将 APK 传到手机点击安装。

## 技术规格

| 项目 | 值 |
|------|------|
| 包名 | com.fengnian.folderdrawer |
| minSdk | 30 (Android 11.0) |
| targetSdk | 34 (Android 14) |
| 架构 | MVVM + Room |
| UI | Material Design 3 |
| 语言 | Kotlin |

## 项目结构

```
com.fengnian.drawer/
├── App.kt                      # Application
├── MainActivity.kt             # 主界面：集合列表
├── CollectionDetailActivity.kt # 集合详情：应用网格
├── AppPickerActivity.kt        # 应用选择器
├── CollectionEditActivity.kt   # 集合编辑（创建/改名/配色）
├── CollectionLauncherActivity.kt# 外部调用入口（透明Activity）
├── IconPackListActivity.kt     # Icon Pack 选择
├── PickerAppData.kt            # 数据传递模型
├── data/                       # Room 数据库层
│   ├── Collection.kt           # 集合实体
│   ├── AppItem.kt              # 应用实体
│   ├── CollectionDao.kt        # 集合 DAO
│   ├── AppItemDao.kt           # 应用 DAO
│   ├── AppDatabase.kt          # Room 数据库
│   └── CollectionRepository.kt # 仓库
├── iconpack/
│   └── IconPackManager.kt      # Icon Pack 解析与图标替换
├── adapter/
│   ├── CollectionAdapter.kt    # 集合列表适配器
│   ├── AppGridAdapter.kt       # 应用网格适配器
│   └── InstalledAppAdapter.kt  # 已安装应用适配器
├── util/
│   ├── AppUtils.kt             # 应用工具（列表/启动/图标）
│   └── ShortcutHelper.kt       # 桌面快捷方式
└── viewmodel/
    └── CollectionViewModel.kt  # ViewModel
```
