# SISE Ores 0.8.0 / SISE Compat 0.5.1 装备统一验证

2026-10-17，基于当前 227 个模组，使用独立客户端和新建测试世界完成验证并安装同一对正式产物。升级前实际版本为 Ores 0.7.1 / Compat 0.5.0。本轮统一相同合成表的装备，撤销为区分这些物品而增加的工具或新摆法；既有非装备材料映射与生产规则保持不变。

验证期间实例另行升级了 SISE Core 0.2.0 → 0.2.1。本轮保留该更新，并使用 Core 0.2.1 补跑最终完整客户端验证；Core 变更为思索教程及界面类，没有配方 JSON 变更。安装前文件保护以这个当前版本为准，原始基线与升级明细另行保留。

Core 更新后的完整运行导出与此前已通过审计的十四组版本逐项相同，包括全部 13 个导出区段及数值表示；因此保留同一份全量配方审计，并重新生成当前客户端的物品、流体、生产、矿脉和装备专项证据。对应证明为 `core-update-audit-equivalence.json`。

## 统一结果

扫描当前启用的数据包、动态生成及模组资源配方。按原生工具、武器、穿戴、耐久组件、装备配方分类及 Curios／Accessories 标签识别装备和饰品，共覆盖 984 条实际启用合成。输入域比较包含标签、原料数量、复合原料、裁边平移、镜像和无序合成。结果为 14 组重复项，没有未分类项。

| 装备组 | 优先原生物品 | 归并的旧物品 |
|---|---|---|
| 青铜斧 | `mekanismtools:bronze_axe` | `ic2:bronze_axe` |
| 青铜靴子 | `mekanismtools:bronze_boots` | `ic2:bronze_boots` |
| 青铜胸甲 | `mekanismtools:bronze_chestplate` | `ic2:bronze_chestplate` |
| 青铜头盔 | `mekanismtools:bronze_helmet` | `ic2:bronze_helmet` |
| 青铜锄 | `mekanismtools:bronze_hoe` | `ic2:bronze_hoe` |
| 青铜护腿 | `mekanismtools:bronze_leggings` | `ic2:bronze_leggings` |
| 青铜镐 | `mekanismtools:bronze_pickaxe` | `ic2:bronze_pickaxe` |
| 青铜锹 | `mekanismtools:bronze_shovel` | `ic2:bronze_shovel` |
| 青铜剑 | `mekanismtools:bronze_sword` | `ic2:bronze_sword` |
| 钻石刀具 | `aquaculture:diamond_fillet_knife` | `prefab:item_swift_blade_diamond` |
| 金刀具 | `aquaculture:gold_fillet_knife` | `prefab:item_swift_blade_gold` |
| 铁刀具／凿子 | `cakesticklib:chisel` | `aquaculture:iron_fillet_knife`、`prefab:item_swift_blade_iron` |
| 木刀具 | `aquaculture:wooden_fillet_knife` | `prefab:item_swift_blade_wood` |
| 石刀具 | `aquaculture:stone_fillet_knife` | `prefab:item_swift_blade_stone` |

优先级继续采用 Mekanism 系 → MekMM → Modern Industrialization → Oritech → EnderIO → LTXI → NeoEcoAE → Ice and Fire → CakestickLib → 其他模组 → IC2CE；列表外同级来源按命名空间稳定排序。Mekanism Tools 归入 Mekanism 系，IC2 始终最后。

同合成表但用途不同的工具也按这个顺序取一个优先物品。例如铁速刃和铁水产刀归并为 CakestickLib 凿子，采用凿子的原生行为，不叠加旧刀具的属性。木、石、金、钻石四种速刃采用 Aquaculture 原生水产刀。石质组的圆石标签与具体圆石存在交集，允许两套配方保留原有不同的输入范围，但重合的网格必须产出同一件装备。

删除 15 条旧装备改摆法补丁：九件 IC2 青铜装备、五种 Prefab 速刃和 CakestickLib 凿子。原材料、数量和镜像支持保留；全部匹配同一网格的路线产出同一种原生物品，每次一件。原配方 ID 保留，不新增装备切石转换或额外工具要求。JEI 与创造列表隐藏旧装备，只显示优先物品。

白名单共 192 个物品组，兼容 458 个旧物品 ID，其中 IC2 73 个；SISE 自有物品仍为 176 个，原来的 178 组非装备映射逐项未变。

## 验证结果

| 检查 | 结果 |
|---|---:|
| 完整客户端与资源重载 | 30,661 项通过 |
| Ores 静态身份转换 | 11,949 项通过 |
| Compat 来源／生产／材料保护 | 345 / 417 / 36 项通过 |
| 原生装备属性识别 | 933 个物品 |
| 装备合成扫描 | 984 条，14 组重复，零未分类 |
| 实际加载配方／序列化类型 | 19,826 / 293 |
| 恢复原装备布局 | 15 条 |
| 其他配方仅替换装备身份 | 4 条 |
| 其余已加载配方逐项一致 | 19,807 条 |
| 既有生产规则 | 83 条文件未变且全部应用 |
| 实际生产对照 | 1,380 条、806 个工艺组通过 |
| 原生配方执行调用 | 13,963 次通过 |
| 配方加载／编码／执行错误 | 0 / 0 / 0 |
| 新增工作台／自动机器冲突 | 0 / 0 |
| 已审核旧装备／物品／流体／化学品配方引用 | 0 |
| 标签缺口／已知纯材料增殖 | 0 / 0 |
| 世界生成及挖掘 | 2,054 项，41 来源／20 版本通过 |
| 其他模组／受保护游戏文件哈希 | 225 / 106 未变 |

真实服务器执行各组原始及镜像网格，遍历全部匹配配方并比较原生结果及默认组件。原生配方编解码逐项验证全部变更：除恢复原装备布局外，只允许当前装备白名单的身份替换；原料数量、产量、加工方式、条件及其他字段均保持。相关回收配方同步接受统一装备。全部配方重载后一致。

原生比较另外覆盖 830 条仅因 Gson 内存数值表示不同而进入编解码对照的配方；两份导出的 JSON 值确认这些配方未变，不计入实际变更数。

原有背包和存档不批量改写。已启用的战利品统一入口转换旧装备时保留名称和附魔；损伤值在优先物品的有效耐久范围内保留。CakestickLib 凿子不使用原版耐久，转换时采用其原生语义，不附加旧刀具的耐久属性。

本轮保留升级前已有的一个非装备工作台候选：酒馆玻璃器皿与豆腐工艺玻璃碗。全材料补扫另报告升级前已存在的万象烹饪面粉未纳入原面粉映射，因此不宣称全包普通材料覆盖全部通过。这些非装备内容和原有 73 对自动机器候选均未因本轮修改；装备审计证明非装备映射不变且所有配方变化仅限上述范围。

## 安装与证据

已安装经过完整验证的两份正式产物：

- `SISE-Ores-0.8.0+26.1.2.jar`，SHA-256 `2db29f026092f70960027ded1a60761a9353652b964c64571e92c9ff87a5ac74`。
- `SISE-Compat-0.5.1+26.1.2.jar`，SHA-256 `d4912782c4fd5fdc539856093b79d7cb9639ae7395dd661f159dfc16265cc734`。

旧版本备份位于 `.work/verification/sise-equipment-20261017/backup`，完整测试实例为 `.work/test-sise-equipment-20261017`。探针不进入正式 JAR。正式安装仅替换 Ores 与 Compat，未改写上游模组、Minecraft 本体或公共库。

冻结记录保存在 `verification/release-0.8.0`，包含本轮审计、完整客户端结果、原生变更证明、扫描、规则及安装哈希；原始升级前导出和文件快照保存在 `.work/verification/sise-equipment-20261017`。两个工程的安装结果分别记录在各自 `verification/installation.json`。
