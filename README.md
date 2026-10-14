# ImageMeta Android · 图片元数据

[![Android build](https://github.com/luchen022/imagemeta-android/actions/workflows/build.yml/badge.svg)](https://github.com/luchen022/imagemeta-android/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

本地、无广告的安卓图片元数据查看与编辑工具，Android 8.0 及以上。

## 下载

每次推送到 `main` 都会自动运行测试并编译 APK。打开 [Actions](https://github.com/luchen022/imagemeta-android/actions/workflows/build.yml)，选择成功的构建，在 **Artifacts → ImageMeta-APK** 下载解压后安装。下载构建产物需要登录 GitHub。

推送 `v*` 标签时，工作流会将 APK 附加到对应的 [Release](https://github.com/luchen022/imagemeta-android/releases)。安装包不存放在源码仓库中。

## 能力与范围

| 格式 | 元数据读取 | 同格式写入副本 | PNG 转换 |
| --- | --- | --- | --- |
| JPEG / JPG | EXIF、GPS、XMP、IPTC 等 | 支持 | 手机能解码时支持 |
| PNG / APNG | EXIF、文本、XMP 等 | 支持；动画数据保留 | 动图禁用 |
| WebP | EXIF、XMP、格式信息 | 支持；动画数据保留 | 动图禁用 |
| HEIC / HEIF | 支持的 EXIF、XMP、容器信息 | 只读 | 取决于系统解码能力 |
| AVIF | Android 12+ 的 EXIF / XMP | 只读 | 取决于系统解码能力 |
| TIFF、GIF、BMP、PSD、ICO、PCX | 解析器支持的格式字段 | 只读 | 取决于系统；所有 GIF 保守禁用转换 |
| DNG、CR2、NEF、NRW、ARW、RW2、ORF、PEF、SRW、RAF | 支持的 EXIF / 厂商字段，具体取决于文件 | 只读 | 系统可完整解码时支持；通常仅可查看信息或缩略图 |
| SVG、JPEG XL、CR3 等未列出格式 | 不保证支持 | 不支持 | 不保证支持 |

“支持”不意味着覆盖某格式的所有编码、所有厂商变种、所有元数据字段。损坏或特殊文件可能无法解析。文件头用于识别可写格式，不能靠改扩展名绕过限制。信息列表最多 3500 项，每个字段最多 32768 字符；界面中的更长字段截断显示，JSON 导出保留读取到的内容。

各格式都可以编辑表单并导出独立的 JSON 信息文件；这些修改**不会写入只读格式的图片内部**。JSON 导入用原图 SHA-256 验证对应关系，防止误用其他图片的信息文件。

## 查看与编辑

- 系统文件选择器打开图片；RAW 可用“选择其他格式”。也可从相册分享到本应用。
- 查看文件名称、系统提供的位置 URI、大小、尺寸、EXIF / GPS / XMP / IPTC 等信息。
- 可编辑 EXIF 拍摄时间、图片修改时间、厂商、型号、作者、版权、处理软件和 GPS 经纬度。
- 地址、来源网址、中文备注保存为 `EXIF UserComment` 内 `ImageMeta/1` 前缀的 ASCII JSON；Unicode 采用 JSON 转义，其他阅读器可能显示结构化文本。已有普通 UserComment 会作为备注保留；保存时转换为本应用结构。
- 地理坐标使用 WGS-84；地址不会自动反向解析，来源网址不会凭空推断。地图按钮交给外部地图应用处理。
- “清空 EXIF 经纬度”删除 EXIF GPS 字段；**不清除 XMP、IPTC、厂商字段等位置线索，不是彻底的隐私清理工具**。编辑 GPS 也不更新这些其他位置字段。
- 只修改其他字段时保留原 GPS 海拔、时间等信息。改变坐标时会清除旧 GPS 附属字段，避免新坐标搭配旧位置数据。
- 如 GPS 被系统隐藏，可以使用“尝试读取原始 GPS”，按需授权照片及媒体位置信息权限。这不获取当前手机位置。
- 保存时先在私有缓存中生成副本、回读字段验证，再通过系统保存对话框写到新文件，并重新读取验证 SHA-256。保存取消、生成错误不会改动原图；文件提供方写入失败可能留下不完整副本，界面会提示失败。

## 转换边界

原图最多 512 MiB。预览缩小至最长边 1400 像素，预览失败仍允许读取信息。

PNG 转换使用完整分辨率，最多 3600 万像素，保留可解码的透明度和方向。它不保留 RAW 特性、HDR、图层、全部原始元数据；只写入当前表单字段。动画禁止静态转换。系统不支持完整解码时停止，不用缩略图冒充原尺寸转换。超大图片仍可能受手机内存限制。

## 构建

JDK 17、Android SDK 35 / Build Tools 35、Gradle 8.9。

```bash
./gradlew assembleDebug testDebugUnitTest
```

`app/build/outputs/apk/debug/app-debug.apk` 为可安装的调试签名 APK。仓库不包含签名密钥。

需要固定签名用于覆盖升级时，在仓库的 **Settings → Secrets and variables → Actions** 中添加 `ANDROID_KEYSTORE_BASE64`，内容为私有开发签名文件的 Base64。工作流会将它还原为 `signing/development.keystore`，并使用 Android 开发默认的别名 `androiddebugkey` 和密码 `android`；与最初源码包中的开发签名兼容。不要将密钥上传到仓库、Issue 或构建日志。

未配置该 Secret 时，CI 使用临时调试签名；不同构建之间不保证能覆盖安装。正式发布版应使用自己的发布签名并另行配置：

```bash
./gradlew assembleRelease
```

包含针对经纬度、日期、URL、JPEG / PNG / WebP 字段读写、中文备注、GPS 删除、压缩像素数据不变、APNG / WebP 动画帧及时间不变、BMP / GIF / TIFF 只读与 JSON 导出的测试。测试不代表已在全部手机、全部格式变种上验证。

## 隐私和依赖

无 INTERNET 权限、无定位追踪权限、无广告或分析 SDK、无应用数据备份。仅用户选择的文件在设备本地缓存中处理；来源网址只作为文本保存。

- AndroidX ExifInterface 1.4.2：Apache-2.0
- metadata-extractor 2.19.0：Apache-2.0
- Adobe XMP Core（传递依赖）：BSD-3-Clause
- JUnit / Robolectric 为测试依赖，不打入 APK。

代码许可证见 LICENSE。使用的第三方库仍遵循各自许可证。
