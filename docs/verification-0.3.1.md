# 0.3.1 兼容修复归并

2026-10-11，SISE Ores 0.3.1 与 SISE Compat 0.2.0 联合构建、验证并安装。

15 条 NeoEcoAE 矿物格式修复、12 条粉末／钢／硅块冲突处理、MI 手动切石保护及 RarityCore 同步快照全部迁入 SISE Compat。Ores 不再包含 `MineralRepairs`、`RecipeSafety`、`ManualStonecuttingMixin` 或 `RaritySyncSnapshotMixin`。配方阶段顺序为 Compat HIGH 修复格式、Ores LOW 统一身份、Compat LOWEST 处理冲突。

17 种材料、102 个等价组、101 个注册物品、221 个来源 ID、材料清单、标签、模型、掉落及世界生成算法均保持。静态材料转换测试通过 5,027 项断言；完整客户端新世界和资源重载通过 13,208 项联合断言，包含材料注册、实际配方、矿石掉落、JEI、网络原料及组件保护。

当前包实际加载 17,644 条配方，加载错误、编码错误、残留旧材料、纯材料增殖和迁移新增工作台冲突均为 0。3 对工作台冲突及 73 对自动机器同输入候选在归并前已存在，本次没有扩展配方修改范围。专用服务端被现有 MI 移植版的客户端类引用阻挡，未计为通过。

此次没有重复执行普通地形全量生成验证；既有结果见 [0.3.0](verification-0.3.0.md)。完整归并范围与实测边界见 [SISE Compat 0.2.0](../../SISE-Compat/docs/verification-0.2.0.md)。窗口原生保护保持原样，其他 208 个 JAR 和 118 个受保护游戏文件的安装前后哈希一致。
