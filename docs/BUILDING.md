# 构建与验证

本仓库的主分支为 `26.1.2`，当前模组版本为 `0.3.0+26.1.2`。

这是面向目标整合包的材料兼容模组，针对实际安装的 NeoForge 26.1.2.114 与配套模组开发。它不是能任意移除依赖的通用材料库。运行时代码在 `src/main/java`，规则在 `materials.json`；资源文件和模组元数据由本地 `tool/build.py` 生成。

Python 脚本统一存放在维护者本地的 `tool/` 目录；`/tool/` 和 `*.py` 均已加入 `.gitignore`，不随源码提交。以下命令仅适用于已另行准备好这些脚本的维护环境。仅克隆此仓库无法直接执行构建或验证命令。

## 构建要求

- Windows、Python 3.10 或更新版本、JDK 25。
- 仓库根目录下已准备好本地 `tool/` 脚本。
- 已安装相应模组的目标实例，其中包含 `mods/` 和已解析的 `libraries/`。
- 类路径需要该实例的 NeoForge、MEK、MEK More Machine、MI、Oritech、LTXI、NeoEcoAE、JEI 等依赖。构建脚本使用实例现有 JAR，不下载或重新分发第三方代码及纹理。

从仓库根目录执行：

```powershell
$env:SISE_INSTANCE_DIR = 'D:\Minecraft\SISE'
$env:SISE_JDK_DIR = 'C:\Java\jdk-25'
python tool/build.py
python tool/test.py
```

产物位于 `build/libs/SISE-Ores-0.3.0+26.1.2.jar`。

如果源码放在原实例的 `.work/SISE-Ores`，无需设置 `SISE_INSTANCE_DIR`。JDK 路径依次使用 `SISE_JDK_DIR`、实例 `.work/devtools/jdk-25`、`JAVA_HOME`。

`tool/build.py` 只清理本工程的 `build/classes` 与 `build/resources`。`tool/test.py` 扫描该实例的实际配方，测试 ID 替换、复合原料语义、幂等性、特殊材料排除、数量和概率保持；结果在被 Git 忽略的 `verification/` 下。脚本移动后，源码、规则、构建产物与验证记录仍使用仓库根目录下的原路径。

## 完整客户端验证

本地 `tool/verify.py`、`tool/audit.py`、`tool/worldgen_report.py` 配合仓库中的 `src/probe` 完成目标实例的集成验证，完整运行需要：

- 实例的 `config/`、`defaultconfigs/`、`kubejs/` 和 `options.txt`；
- 已安装 SISE Compat 0.1.1（集成探针同时检查其配方修复）；
- 实例中的 `.work/instance-maintenance-20261008/verify_pcl_launch.py` 启动参数生成器；
- 本地 `verification/baseline-worldgen/` 中此前验证的配方基线，用于 `tool/audit.py` 对照。

这些本地环境、完整配方导出、客户端日志、存档和启动参数不随源码提交。已备齐本地构建工具但缺少集成验证数据时，只能运行构建与静态回归，不能把它们称为完整游戏验证。

```powershell
python tool/verify.py prepare
python tool/verify.py launch
python tool/audit.py
python tool/worldgen_report.py
```

客户端测试使用独立的 `.work/test-sise-ores-worldgen-20261011` 目录和离线测试身份；不读取玩家存档。测试探针不会包含在正式模组 JAR 中。已完成的验证摘要见 [0.3.0 验证记录](verification-0.3.0.md)。

本地 `tool/install.py` 面向原实例的受控升级：必须已有本地安装记录、完整客户端结果、配方审计与世界生成报告，且待安装 JAR 与测试实例中的 JAR 哈希相同。新的部署环境可以在完成验证后手动安装正式 JAR；不要将探针 JAR 放入正式实例。

本地 `tool/plan_worldgen.py` 是维护材料白名单的人工审核辅助脚本；`tool/update_worldgen_audit.py` 是从原实例的 SISE Compat 工程迁移审计器和冻结基线的开发辅助脚本，都不是正常构建步骤。
