# 构建与验证

本仓库的主分支为 `26.1.2`，当前模组版本为 `0.3.0+26.1.2`。

这是 project02 整合包的材料兼容模组，针对实际安装的 NeoForge 26.1.2.114 与配套模组开发。它不是能任意移除依赖的通用材料库。运行时代码在 `src/main/java`，规则在 `materials.json`；资源文件和模组元数据由 `build.py` 生成。

## 构建要求

- Windows、Python 3.10 或更新版本、JDK 25。
- 已安装相应模组的 project02 实例，其中包含 `mods/` 和已解析的 `libraries/`。
- 类路径需要该实例的 NeoForge、MEK、MEK More Machine、MI、Oritech、LTXI、NeoEcoAE、JEI 等依赖。构建脚本使用实例现有 JAR，不下载或重新分发第三方代码及纹理。

从仓库根目录执行：

```powershell
$env:SISE_INSTANCE_DIR = 'D:\Minecraft\project02'
$env:SISE_JDK_DIR = 'C:\Java\jdk-25'
python build.py
python test.py
```

产物位于 `build/libs/SISE-Ores-0.3.0+26.1.2.jar`。

如果源码放在原实例的 `.work/SISE-Ores`，无需设置 `SISE_INSTANCE_DIR`。JDK 路径依次使用 `SISE_JDK_DIR`、实例 `.work/devtools/jdk-25`、`JAVA_HOME`。

`build.py` 只清理本工程的 `build/classes` 与 `build/resources`。`test.py` 扫描该实例的实际配方，测试 ID 替换、复合原料语义、幂等性、特殊材料排除、数量和概率保持；结果在被 Git 忽略的 `verification/` 下。

## 完整客户端验证

`verify.py`、`audit.py`、`worldgen_report.py` 和 `src/probe` 是原 project02 实例的集成验证工具，完整运行需要：

- 实例的 `config/`、`defaultconfigs/`、`kubejs/` 和 `options.txt`；
- 已安装 SISE Compat 0.1.1（集成探针同时检查其配方修复）；
- 实例中的 `.work/instance-maintenance-20261008/verify_pcl_launch.py` 启动参数生成器；
- 本地 `verification/baseline-worldgen/` 中此前验证的配方基线，用于 `audit.py` 对照。

这些本地环境、完整配方导出、客户端日志、存档和启动参数不随源码提交。没有这些前置数据时，只能运行构建与静态回归，不能把它们称为完整游戏验证。

```powershell
python verify.py prepare
python verify.py launch
python audit.py
python worldgen_report.py
```

客户端测试使用独立的 `.work/test-sise-ores-worldgen-20261011` 目录和离线测试身份；不读取玩家存档。测试探针不会包含在正式模组 JAR 中。已完成的验证摘要见 [0.3.0 验证记录](verification-0.3.0.md)。

`install.py` 面向原实例的受控升级：必须已有本地安装记录、完整客户端结果、配方审计与世界生成报告，且待安装 JAR 与测试实例中的 JAR 哈希相同。新的部署环境可以在完成验证后手动安装正式 JAR；不要将探针 JAR 放入正式实例。

`plan_worldgen.py` 是维护材料白名单的人工审核辅助脚本；`update_worldgen_audit.py` 是从原实例的 SISE Compat 工程迁移审计器和冻结基线的开发辅助脚本，都不是正常构建步骤。
