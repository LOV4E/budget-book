# 简账 · 黑白预算记账

一个极简的黑白配色预算记账 Android 应用。无网络、无权限、体积不到 100 KB。

## 安装包

| 项 | 值 |
| --- | --- |
| 文件 | `JianZhang-1.0.apk` |
| 包名 | `com.dsh.budgetbook` |
| 版本 | 1.0 (versionCode 1) |
| 系统要求 | Android 5.0 (API 21) 及以上 |
| targetSdk | 34 (Android 14) |
| 签名 | APK Signature Scheme v1 + v2 + v3，已用自签名证书签名 |
| 权限 | **无**（不联网、不读写外部存储） |
| 体积 | 约 71 KB |

### 安装方式

1. 把 `JianZhang-1.0.apk` 传到手机。
2. 用文件管理器点击安装，按提示允许「安装未知来源应用」。
3. 因为是自签名证书（非 Google Play 上架签名），首次安装会有系统提示，属正常现象。

命令行安装（需自备 adb）：

```bash
adb install -r JianZhang-1.0.apk
```

## 功能

### 功能一 · 自定义每月预算
- 顶部黑色区域点击「设置」或「点击设置本月预算」即可输入金额。
- 预算是**按月独立存储**的（键为 `yyyy-MM`），可以给不同月份设不同预算。
- 输入框留空并确定 = 清除该月预算。
- 头部 `‹` `›` 可切换月份，随时回看或预先设置其他月份的预算。
- 设置后显示：预算金额、已支出、剩余（超支时显示「超出」），以及一条进度条。

### 功能二 · 手动选择收入 / 支出
- 主界面底部「记一笔」进入记账页。
- 顶部「支出 / 收入」是显式的手动二选一控件，选中项黑底白字。
- 切换类型时，分类选项会跟着切换（支出：餐饮/交通/购物/居住/娱乐/医疗/学习/其他；收入：工资/奖金/理财/兼职/报销/红包/其他）。
- 金额、备注、日期（日期选择器）均可手填。

### 其他
- 主界面按月份列出明细，列表项显示分类、备注、日期与金额（收入显示 `+`，支出显示 `-`）。
- **长按任意一条记录**可删除。
- 数据保存在本机 `SharedPreferences`（JSON），金额以「分」为整数存储，不使用浮点数，避免精度误差。

## 界面设计

- 配色只有黑（`#000000`）与白（`#FFFFFF`），辅以中性灰做层级。
- 状态栏 / 导航栏为纯黑，顶部预算区为反白黑底白字，下方明细区白底黑字。
- 无圆角卡片、无阴影、无彩色图标；启动图标是白底黑面上的几何 `¥` 标记。

## 源码结构

```
budget-book/
├─ app/
│  ├─ AndroidManifest.xml
│  ├─ src/com/dsh/budgetbook/
│  │  ├─ MainActivity.java      主界面：月份切换、预算展示、明细列表
│  │  ├─ AddActivity.java       记账页：收入/支出选择、金额、分类、备注、日期
│  │  ├─ Store.java             SharedPreferences + JSON 持久化
│  │  ├─ Txn.java               记录模型（收入/支出）
│  │  └─ Money.java             金额格式化与解析（分 <-> 字符串，与系统语言无关）
│  └─ res/
│     ├─ layout/                activity_main / activity_add / item_txn / dialog_budget
│     ├─ drawable/              黑白形状背景（按钮、分段控件、标签、进度条）
│     ├─ values/                colors / strings / styles
│     └─ mipmap-*/             启动图标（含 API 26+ 自适应图标）
├─ tools/
│  ├─ setup-toolchain.ps1       下载 JDK 17
│  ├─ fetch-sdk.ps1             直连 Google 仓库下载 platform-34 / build-tools 34.0.0
│  ├─ make-icons.py             生成各密度启动图标
│  ├─ build.ps1                 完整构建：aapt2 → javac → d8 → zipalign → apksigner
│  ├─ add-dex.py                把 classes.dex 写入 APK
│  ├─ verify.py                 布局 id / 强制转换 / dex 类表静态校验
│  └─ jvmtest/                  Money 逻辑的 JVM 单元测试
└─ JianZhang-1.0.apk            构建产物
```

## 重新构建

```powershell
pwsh -File tools\setup-toolchain.ps1   # 首次：下载 JDK 17
pwsh -File tools\fetch-sdk.ps1         # 首次：下载 Android platform 34 + build-tools 34.0.0
python tools\make-icons.py             # 生成图标
pwsh -File tools\build.ps1             # 构建并签名
python tools\verify.py                 # 静态校验
```

构建不依赖 Gradle，直接调用 Android SDK 的 `aapt2` / `d8` / `zipalign` / `apksigner`，
因此不需要联网拉取任何 Maven 依赖。

## 已验证 / 未验证

**已验证**
- `aapt2 dump badging`：包名、版本、minSdk 21、targetSdk 34、应用名「简账」、启动 Activity 均正确。
- `apksigner verify`：v1 / v2 / v3 三种签名方案全部通过。
- APK 内含 `AndroidManifest.xml`、`resources.arsc`、`classes.dex`，且只有一个 dex。
- 全部 27 处 `R.id` 引用都能在对应布局中找到，且强制转换类型与布局标签一致（不会 NPE / ClassCastException）。
- `Money` 的 26 项金额格式化与解析用例在 JVM 上全部通过。

**未验证**
- 未做真机 / 模拟器运行验证。这台机器上 Windows Hypervisor Platform 处于关闭状态
  （需重启才能启用），模拟器无法启动，因此没有实际渲染截图，界面为按布局代码推导的效果。
