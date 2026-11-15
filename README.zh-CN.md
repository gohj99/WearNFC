# WearNFC

WearNFC 是一款适用于 Wear OS 手表的 NFC 卡片管理与模拟应用，包含手表端应用和 Android 手机端伴侣应用。用户可以使用手机读取支持的卡片，将卡片保存到手表，并从手机、手表应用或 Wear OS Tile 切换当前使用的卡片。

## 手表端必须安装的模块

> [!IMPORTANT]
> 手表必须提前安装并正常启用 [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) 模块，WearNFC 才能模拟卡片。没有这个模块，应用无法把模拟卡片应用到手表的 NFC 系统中。

请先通过 KernelSU、BakaSU 或手表使用的其他 Root 管理器，在手表上安装并启用该模块；首次运行 WearNFC 时，请授予手表端 WearNFC Root 权限。该模块属于独立项目，不包含在本仓库中。

手机端 **不需要 Root**。只有手表端需要 Root，因为手表应用会调用模块提供的 `nfcconf` 命令，并修改手表的 NFC 配置文件来应用卡片。

## 功能

- 使用手机 NFC 读取卡片，并将卡片数据发送到已配对的手表。
- 在手表上持久化保存卡片信息和原始 dump 数据。
- 在手机端添加、编辑、删除和切换卡片。
- 如果手表 NFC 读卡器支持该卡片，也可以直接在手表端添加和管理卡片。
- 切换当前使用的模拟卡片，并恢复手表原始 NFC 配置。
- 提供 Wear OS Tile，快速切换到下一张已保存的卡片。
- 通过 Wear OS Data Layer 保持手机界面与手表状态同步。

卡片列表和 dump 文件保存在手表上。手机端显示当前连接手表返回的卡片快照，不保存离线卡片数据库。

## 使用要求

- 一台拥有 NFC、且受 [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) 模块支持的 Wear OS 手表。
- 手表已安装并启用该模块。
- 手表已获取 Root，且已向 WearNFC 授予 Root 权限。
- 一台支持 NFC 的 Android 手机，用于从手机读取卡片。
- 手表与手机已通过 Wear OS 伴侣连接完成配对，并且 Google Play 服务可用于 Data Layer 通信。
- 手机和手表的 Android 版本为 11 或更高；项目当前 `minSdk` 为 30。

## 支持的卡片与限制

当前读卡器和手表模块支持 UID 为 4 字节的 NFC-A 卡片。MIFARE Classic 1K 和 4K 卡片在能够读取其内存时可以使用。

读取 MIFARE Classic 时，手机会依次尝试默认密钥、全零密钥和常见的 `A0 A1 A2 A3 A4 A5` 密钥。加密或无法读取的扇区不会被复制，应用不会把未读取的数据当作已克隆数据。如果最终只能读取 UID，应用会明确提示当前只读取到了 UID。

手机与手表之间使用项目定义的 flat dump 格式传输数据：

- MIFARE Classic 1K：1,024 字节
- MIFARE Classic 4K：4,096 字节
- 最多保存卡片：100 张

UID 不是 4 字节的卡片、不支持的 NFC 技术类型，以及不支持的 MIFARE 容量会被读卡器拒绝。

## 快速开始

### 1. 准备手表

1. 在手表上安装并启用 [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) 模块。
2. 通过 KernelSU、BakaSU 或手表使用的 Root 管理器，向 WearNFC 授予 Root 权限。
3. 安装并至少打开一次 WearNFC 手表端 APK。
4. 在手表应用的模拟设置中选择已安装的模块，确认页面显示 NFC 模块可用。

请在添加或切换卡片前完成以上步骤。如果没有模块，手机仍可能显示连接状态并管理已经同步的卡片元数据，但手表无法应用或切换模拟卡片。

### 2. 安装手机端伴侣应用

在已配对的 Android 手机上安装 mobile APK 并打开应用。手机端会通过 Wear OS Data Layer 查找可连接的手表，连接成功后显示手表确认返回的卡片列表。

### 3. 添加和使用卡片

1. 在手机端点击“添加卡片”，将卡片贴近手机的 NFC 感应区域。
2. 读取时保持卡片不动。读取完成后填写名称、备注、展示卡号和颜色，再保存到手表。
3. 点击“设为当前卡片”，将卡片应用到手表。
4. 使用手表应用或 Wear OS Tile 在已保存的卡片之间切换。
5. 选择“原始 NFC”，停止模拟并恢复手表原本的 NFC 配置。

在卸载或更新上述模块前，请先切换到“原始 NFC”。只有模块和 Root 权限仍然可用时，应用才能恢复原始配置。

## 从源码构建

项目包含 `mobile`、`wear` 和 `shared` 三个 Gradle 模块，使用 Kotlin、Jetpack Compose 和 Wear OS Data Layer。

```bash
# macOS / Linux
./gradlew :mobile:assembleDebug :wear:assembleDebug :shared:test

# Windows
gradlew.bat :mobile:assembleDebug :wear:assembleDebug :shared:test
```

生成的 debug APK 位于：

- `mobile/build/outputs/apk/debug/mobile-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

请将 mobile APK 安装到手机，将 wear APK 安装到手表。为保证伴侣通信正常，两端 APK 应使用相同的 application ID 和签名密钥构建。

## 项目结构

```text
mobile/   Android 手机端：NFC 读取、卡片编辑和手表通信
wear/     Wear OS 手表端：卡片存储、NFC 模块控制、卡片切换和 Tile
shared/   共享卡片模型、NFC dump 读取器，以及手机/手表通信协议
```

手表端卡片管理器使用 DataStore 持久化元数据，并将原始 dump 保存在应用私有目录 `files/ic/` 中。切换卡片时，应用会根据原始备份重写模块的 NFC 路由和 NXP 配置，刷新 NFC 服务，并且只在操作成功后更新当前卡片状态。

## 故障排查

### 手机提示 NFC 模块不可用

确认手表已经安装并启用 [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) 模块，WearNFC 已获得 Root 权限，且应用中选择的是手表实际安装的模块。手表应用启动时会执行模块的 `nfcconf status` 来检查可用性。

### 手机无法连接手表

确认两端都安装了应用、手表与手机已经配对、Google Play 服务可用，并且至少打开过一次手表端应用。如果当前有多台手表可连接，请在手机端选择目标手表。

### 读取结果只有 UID

卡片可能不是 MIFARE Classic，或者卡片扇区使用了读卡器未知的密钥。无法读取的数据不会被复制。

### 切换卡片失败

保持手表处于唤醒状态，并检查 Root 权限和必需模块是否仍然可用。修改模块配置前，请先切换回“原始 NFC”。

## 开源协议

本项目使用 GNU General Public License v3.0，详见 [LICENSE](LICENSE)。

English documentation: [README.md](README.md)
