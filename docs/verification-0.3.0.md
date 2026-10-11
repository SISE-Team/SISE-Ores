# SISE Ores 0.3.0+26.1.2 世界生成验证

在含 SISE Compat 0.1.1 的完整 208 模组环境中启动独立客户端、新建普通地形世界；没有读取或修改玩家存档。
统一 10 种金属的 35 个旧矿石 ID，注册 20 个 SISE 矿石版本。石头、深板岩、末地石分开；原版及其他特殊矿物保持原身份。

| 检查 | 结果 |
|---|---:|
| 全部有效生成配置编码与旧目标排查 | 575 |
| 对照原文件、确认仅更换矿石身份的生成配置 | 37 |
| 主世界实际生成区块扫描 | 25 |
| 末地实际生成区块扫描 | 9 |
| 扫描区块中残留本次白名单旧矿石 | 0 |
| 真实 OreFeature 矿脉和 WorldGenRegion 放置 | 35 / 35 通过 |
| 普通挖掘、精准采集、采掘等级 | 20 / 20 通过 |
| 时运 III | 20 种矿石各 48 次，身份、数量范围与增产通过 |
| 世界生成专项断言 | 2005 |
| 完整客户端、配方、SISE Compat 和重载断言 | 13156 |
| 静态配方转换回归断言 | 21152 |

## 实际自然生成抽样

区块扫描发生在测试人工放矿之前。抽样不是全部维度和全部地形的穷举；没有把人工放置的测试矿脉算入自然生成数量。

| 维度 | 矿石 ID | 数量 |
|---|---|---:|
| 主世界 | `sise_ores:deepslate_lead_ore` | 2877 |
| 主世界 | `sise_ores:deepslate_nickel_ore` | 1953 |
| 主世界 | `sise_ores:deepslate_osmium_ore` | 472 |
| 主世界 | `sise_ores:deepslate_platinum_ore` | 333 |
| 主世界 | `sise_ores:deepslate_silver_ore` | 758 |
| 主世界 | `sise_ores:deepslate_tin_ore` | 1827 |
| 主世界 | `sise_ores:deepslate_titanium_ore` | 1131 |
| 主世界 | `sise_ores:deepslate_tungsten_ore` | 329 |
| 主世界 | `sise_ores:deepslate_uranium_ore` | 1266 |
| 主世界 | `sise_ores:lead_ore` | 213 |
| 主世界 | `sise_ores:nickel_ore` | 191 |
| 主世界 | `sise_ores:osmium_ore` | 119 |
| 主世界 | `sise_ores:silver_ore` | 142 |
| 主世界 | `sise_ores:tin_ore` | 227 |
| 主世界 | `sise_ores:titanium_ore` | 99 |
| 主世界 | `sise_ores:tungsten_ore` | 17 |
| 主世界 | `sise_ores:uranium_ore` | 24 |
| 末地 | `sise_ores:endstone_platinum_ore` | 25 |

## 优先级与外观

Mekanism → Mekanism More Machine → Modern Industrialization → 其他模组。对应物品来源均为 SISE Ores。

| 统一矿石 | 参考来源 |
|---|---|
| 深层锡矿石 | `mekanism:deepslate_tin_ore` |
| 锡矿石 | `mekanism:tin_ore` |
| 深层铅矿石 | `mekanism:deepslate_lead_ore` |
| 铅矿石 | `mekanism:lead_ore` |
| 深层银矿石 | `mekmm:deepslate_silver_ore` |
| 银矿石 | `mekmm:silver_ore` |
| 深层镍矿石 | `modern_industrialization:deepslate_nickel_ore` |
| 镍矿石 | `modern_industrialization:nickel_ore` |
| 铝矿石 | `neoecoae:aluminum_ore` |
| 铂矿石 | `modern_industrialization:platinum_ore` |
| 深层铂矿石 | `oritech:deepslate_platinum_ore` |
| 末地石铂矿石 | `oritech:endstone_platinum_ore` |
| 深层钛矿石 | `ltxi:deepslate_titanium_ore` |
| 钛矿石 | `modern_industrialization:titanium_ore` |
| 深层钨矿石 | `modern_industrialization:deepslate_tungsten_ore` |
| 钨矿石 | `modern_industrialization:tungsten_ore` |
| 深层铀矿石 | `mekanism:deepslate_uranium_ore` |
| 铀矿石 | `mekanism:uranium_ore` |
| 深层锇矿石 | `mekanism:deepslate_osmium_ore` |
| 锇矿石 | `mekanism:osmium_ore` |

## 本轮发现并修复

- 10 条熔炼／高炉配方的物品备选列表在换成标签后需使用 compound ingredient。保持同一原料槽任选一种矿石、产量、经验与烧制时间。
- MEK More Machine 两种银矿原掉落表无条件掉自身。新 SISE 银矿按 MEK 标准掉粗银，精准采集掉矿石，时运生效；外观、硬度及工具等级保持优先来源。
- Oritech 3 个自定义生成配置单独统一周围矿石和铀矿斑，资源节点本体、晶簇、油层与权重保留。

## 生效与边界

- 安装后重启游戏，在新生成区块生效；未自动替换已生成区块或容器库存，也未开启补矿。
- 保留原有高度、维度、生物群系、矿脉规模、频率与暴露概率；没有关闭原有生成器或重新平衡总矿量。
- 旧矿石可继续加工，新增 35 条切石机 1:1 手动转换。原版矿石、铝土矿、晶质铀矿、充能晶体和粗矿簇不混并。
- 新矿石的源配方兼容与机器配方回归详见 本地完整配方审计报告；没有声称所有机器经济循环均完成穷举。

测试 JAR SHA-256：`b8b809ce86be0ac0bfead28f0a68cc5a31a56380b06fb1ac4128e3cd6988f07e`



## 正式安装与回归结果

已安装 `SISE-Ores-0.3.0+26.1.2.jar`，SHA-256 与完成测试的 JAR 一致。其余 207 个模组和 3 个游戏本体文件的哈希保持不变；旧 0.2.0 JAR 已保存到工程 backup 目录。

加载 16892 条配方，实际加载错误 0；12265 次配方执行调用无异常。工作台冲突 0，相对安装基线新增自动机器同输入候选 0，全部配方重载一致。原有 73 对机器候选保留，不代表全包所有机器循环已经证明无冲突。
