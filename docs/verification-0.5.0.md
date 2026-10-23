# SISE Ores 0.5.0 / SISE Compat 0.2.6 板材与零件统一验证

2026-10-14，扫描当前实例全部 222 个模组 JAR 的公共标签、英文／中文显示名、物品模型、实际注册表与制造／回收配方。正式产物已经安装，旧版 Ores 0.4.0 与 Compat 0.2.5 已备份。当前 IC2CE 1.0.1 原 JAR 保持不变。

本轮新增 18 组、46 个来源 ID；总计 36 种材料、155 个等价组、153 个 SISE 注册物品和 389 个来源 ID。IC2CE 纳入 55 个来源 ID，其中本轮新增 7 种普通金属板与碳纤维板。

## 新增范围

- 普通铁、金、铜、锡、铅、青铜、钢、银、硅、钛板。普通金属板沿用一锭一板的机器成本，IC2 切块沿用一块九板。
- IC2 与 CakestickLib 的碳纤维复合板；保留各自网材／纤维制造路线和数量。
- CakestickLib、Industrial Foregoing 与 Oritech 的普通塑料片；保留九片压一塑料块和一块拆九片。
- 铜、铁、金、钢、钛、木齿轮。保留工作台与自动组装的原材料、产量及加工成本。
- 杆与线材也已扫描，当前公共标签与同名物品没有新增普通等价组。电缆保留原模组电网行为。

## 配方与加工阶段

新增 LTXI 冲压配方的物品输入／输出支持，保留板材与齿轮冲压模式、取料数量、耗时和随机产物参数。IC2 原生轧制、切块、锻造、压缩与磨粉继续使用原机器和工作台；本轮没有把制造冲突改成切石机。旧物品的单向 1:1 迁移继续使用切石机。

SISE Compat 新增七条带精确原配方校验的普通钢制造规则，覆盖 IC2 轧制／切块、MI 压缩／手工锻造、CakestickLib 钢板／钢齿轮。输入限定为审核后的普通钢，排除生物钢；数量、原配方 ID、处理模式、输出和工具损耗均保留。

- IC2 致密板保留九张普通板的压缩阶段；八张板不能制作。MI 大型板、弯曲板仍保留原身份。
- 普通硅板、掺杂硅板与 AE2 印刷硅板分开；印刷硅是电路中间产物。
- MI 碳粉压缩板与 IC2／CakestickLib 碳纤维复合板分开，不通过同名进行互换。
- IC2 橡胶薄片可铺设为方块，MI 橡胶片是合成部件，保留各自行为。HDPE、强化碳片和其他专用聚合物不纳入普通塑料片。

## 完整游戏实测

在独立普通地形测试世界中运行完整客户端，测试后重新加载资源并再次执行检查；探针不包含在正式 JAR，也没有读取玩家存档。

| 检查 | 结果 |
|---|---:|
| 完整客户端与资源重载断言 | 21330 通过 |
| 静态配方转换断言 | 9084 通过 |
| 110 条来源保护、幂等、用户覆盖及元数据断言 | 441 通过 |
| 既有材料冲突规则断言 | 36 通过 |
| 实际加载配方 | 18940 |
| 工作台输入域检查 | 8985 条，9002338 对比较 |
| 工作台冲突候选／实际确认冲突 | 0 / 0 |
| 配方加载／编码错误、遗漏来源引用、公共标签缺口 | 0 |
| 原生工作台／机器输入执行检查 | 13607 通过，错误 0 |
| MI 有效机器配方及重载 | 2949 条，遗留引用 0，重载一致 |
| 已知纯材料单位检查 | 1116 条，材料增殖 0 |
| 新增自动机器同输入冲突候选 | 0 |
| 注册的所有来源 ID 与同名候选审核 | 无遗漏、无未决候选 |
| 矿石生成／挖掘回归 | 41 个来源、20 个宿主版本，2054 项断言通过 |
| 其余模组／受保护游戏文件哈希 | 220 / 106 保持 |

专项检查实际调用 IC2 机器 API，确认统一锭轧一板、块切九板、九板压致密板、锻造锤返还及耐久。MI 的原生取料匹配接受各来源板和 SISE 板；LTXI 的原生冲压匹配、产物生成与实际取料通过，错误的冲压模式被拒绝。塑料块使用混合来源的九片材料实际压块、拆分。普通钢制造拒绝生物钢；特殊板材与橡胶块／部件区别保留。

IC2 原生 API 全量检查覆盖 280 条机器配方、238 项统一材料输入和 52 条工作台配方，重载后再次通过。

保留 73 对原有自动机器同输入候选，本轮没有新增。纯材料守恒只检查能明确确定单位的配方，没有声称所有机器经济循环已经穷举。完整日志、配方导出、截图及安装回执保存在维护环境的 `verification/` 与 `.work/test-sise-ores-plates-20261014/`。

## 新纳入的来源 ID

| 统一目标 | 来源 |
|---|---|
| `sise_ores:iron_plate` | `cakesticklib:iron_plate`, `ic2:iron_plate`, `modern_industrialization:iron_plate` |
| `sise_ores:gold_plate` | `cakesticklib:gold_plate`, `ic2:gold_plate`, `ltxi:gold_plate`, `modern_industrialization:gold_plate` |
| `sise_ores:copper_plate` | `cakesticklib:copper_plate`, `ic2:copper_plate`, `ltxi:copper_plate`, `modern_industrialization:copper_plate` |
| `sise_ores:tin_plate` | `ic2:tin_plate`, `modern_industrialization:tin_plate` |
| `sise_ores:lead_plate` | `ic2:lead_plate`, `modern_industrialization:lead_plate` |
| `sise_ores:bronze_plate` | `ic2:bronze_plate`, `modern_industrialization:bronze_plate` |
| `sise_ores:steel_plate` | `cakesticklib:steel_plate`, `ic2:steel_plate`, `modern_industrialization:steel_plate` |
| `sise_ores:silver_plate` | `ltxi:silver_plate`, `modern_industrialization:silver_plate` |
| `sise_ores:silicon_plate` | `ltxi:silicon_plate`, `modern_industrialization:silicon_plate` |
| `sise_ores:titanium_plate` | `ltxi:titanium_plate`, `modern_industrialization:titanium_plate` |
| `sise_ores:carbon_fiber_plate` | `cakesticklib:carbon_plate`, `ic2:carbon_plate` |
| `sise_ores:plastic_sheet` | `cakesticklib:plastic`, `industrialforegoing:plastic`, `oritech:plastic_sheet` |
| `sise_ores:iron_gear` | `cakesticklib:iron_gear`, `enderio:iron_gear`, `industrialforegoing:iron_gear`, `modern_industrialization:iron_gear` |
| `sise_ores:gold_gear` | `cakesticklib:gold_gear`, `industrialforegoing:gold_gear`, `modern_industrialization:gold_gear` |
| `sise_ores:copper_gear` | `cakesticklib:copper_gear`, `modern_industrialization:copper_gear` |
| `sise_ores:steel_gear` | `cakesticklib:steel_gear`, `modern_industrialization:steel_gear` |
| `sise_ores:titanium_gear` | `ltxi:titanium_gear`, `modern_industrialization:titanium_gear` |
| `sise_ores:wooden_gear` | `ae2cs:wooden_gear`, `cakesticklib:wooden_gear` |

## 已安装产物

- `SISE-Ores-0.5.0+26.1.2.jar`，SHA-256：`12e28c6f20f862086925920f48960ea307fb8fba13ea19962691073bfda72786`。
- `SISE-Compat-0.2.6+26.1.2.jar`，SHA-256：`73474f4c6d34c921cf6544d07687162140e5cb4c857e54a95e26e60bf285d90e`。

重启游戏后生效。旧物品仍可作为等价配方输入或按原有迁移配方转换；本次没有扫描改写库存或存档。
