# SISE Ores 0.6.0 / SISE Compat 0.3.0 UU 与流体统一验证

2026-10-16，扫描实际安装的 222 个模组 JAR、341 个流体注册 ID 与 89 个化学品 ID，以及中文／英文名称、公共标签、桶、配方和硬编码机器入口。基线使用升级前实际安装的 Ores 0.5.0、Compat 0.2.6 与当前上游 JAR；没有替换 IC2CE 或修改游戏本体。

共 178 个物品组、176 个 SISE 物品、443 个旧物品 ID，本轮补入 54 个；IC2CE 共覆盖 64 个物品 ID。流体另有 18 组、72 个旧静止／流动 ID、46 个旧桶与一组 UU 化学品。

## 材料与显示

- UU 固体、UU 液体、UU 化学品分别归属各自注册表中的 `sise_ores:uu_matter`；保留原有物态，UU 固体转化学品的 1→500 配方不改数量，不新增固液互转。
- 废料和普通硅晶圆归并；保留硅晶圆各制造路线原来的用料、流体与产量。掺杂硅板和印刷硅继续保持工艺阶段。
- IC2 九份废料盒为 `sise_ores:scrap_box`，MekMM 的 64 份废料加化学品盒为 `sise_ores:compressed_scrap_box`，分别显示“废料盒（9 份）”和“压缩废料盒（64 份）”。两者不等价互换，压缩盒不能冒充 IC2 九份盒拆取掉落。
- JEI 浏览器、配方原料轮换、创造标签页与搜索隐藏审核清单中的旧身份。对应统一物品、流体与 UU 化学品显示 SISE 来源。
- 流体继承优先来源的 FluidType 渲染／物理属性、公共标签和燃料数据；桶容量仍为 1000 mB。组件、序列化、网络和事务式传输保持原数据与体积。

## 原生机器与边界

SISE Compat 在原生堆栈、模板和传输资源创建时规范化身份，兼容精确原料比较及 Titanium 的流体比较；IC2／MI 的 UU 生产、复制及流体引用接入统一对象。旧 IC2 桶补齐 NeoForge 流体接口。IC2 废料盒的硬编码掉落在保持权重与数量的前提下统一。

实测调用 IC2 固体／液体 UU 制造、复制机的液体扣除、废料增幅、废料盒配方与掉落，Industrial Foregoing 乳胶处理和生物燃料发电，以及 MI 流体燃料数据。制造机支付完整原能量后才产出，无能量不能再次生成；乳胶保持 750 mB 加 500 mB 水，生物燃料保持每次消耗 1 mB。

IC2 普通蒸汽适配器原来的 10→16 路线在输入输出成为同一身份后会增加同物质体积，故停用该身份转换；过热蒸汽路线保留。低温液氢／液氧／液氯、同位素、过热／高压蒸汽、奇异物质和 XP/mB 不同的经验流体保留。共享宽泛标签不会自动推导等价。

## 完整验证

完整客户端使用独立新建世界，执行检查、生成／挖掘回归、JEI／创造列表检查和资源重载，再执行检查。探针没有安装到正式实例。

| 检查 | 结果 |
|---|---:|
| 客户端与重载断言 | 27583 通过 |
| 静态配方转换断言 | 10958 通过 |
| 配方来源保护／既有材料冲突断言 | 441 / 36 通过 |
| 实际加载配方／序列化类型 | 18994 / 280 |
| 工作台冲突候选／新增 | 0 / 0 |
| 新增自动机器同输入候选 | 0 |
| 加载／编码／执行错误 | 0 / 0 / 0 |
| 原生配方执行调用 | 13661 通过 |
| 旧物品引用／旧液体化学品引用 | 0 / 0 |
| MI 派生配方／旧流体化学品引用 | 2949 / 0 |
| 物品标签缺口／已知纯材料增殖 | 0 / 0 |
| 实际流体同名候选／未决 | 12 / 0 |
| 世界生成与挖掘 | 41 个来源、20 个版本，2054 断言通过 |
| 其他模组／受保护游戏文件哈希 | 220 / 106 保持 |

保留 73 对原有自动机器同输入候选。完整运行检查与已知单位守恒不表示穷举了所有跨机器经济循环。配方重载结果一致。没有把本轮制造冲突转移到切石机，既有旧物品单向迁移配方保留。

## 流体来源

| SISE 目标 | 原静止流体 |
|---|---|
| `sise_ores:uu_matter` | `ic2:uu_matter`, `modern_industrialization:uu_matter` |
| `sise_ores:steam` | `ic2:steam`, `mekanism:steam`, `modern_industrialization:steam`, `oritech:still_steam` |
| `sise_ores:heavy_water` | `ic2:heavy_water`, `mekanism:heavy_water`, `modern_industrialization:heavy_water` |
| `sise_ores:hydrogen` | `ic2:hydrogen`, `ltxi:hydrogen`, `modern_industrialization:hydrogen` |
| `sise_ores:oxygen` | `ic2:oxygen`, `ltxi:oxygen`, `modern_industrialization:oxygen` |
| `sise_ores:chlorine` | `ltxi:chlorine`, `modern_industrialization:chlorine` |
| `sise_ores:nitrogen` | `ltxi:nitrogen`, `modern_industrialization:nitrogen` |
| `sise_ores:methane` | `ltxi:methane`, `modern_industrialization:methane` |
| `sise_ores:sulfuric_acid` | `cakesticklib:sulfuric_acid`, `ltxi:sulfuric_acid`, `mekanism:sulfuric_acid`, `modern_industrialization:sulfuric_acid`, `oritech:still_sulfuric_acid` |
| `sise_ores:hydrochloric_acid` | `ltxi:hydrochloric_acid`, `modern_industrialization:hydrochloric_acid` |
| `sise_ores:creosote` | `ic2:creosote`, `modern_industrialization:creosote` |
| `sise_ores:crude_oil` | `cakesticklib:crude_oil`, `modern_industrialization:crude_oil`, `oritech:still_oil` |
| `sise_ores:diesel` | `modern_industrialization:diesel`, `oritech:still_diesel` |
| `sise_ores:naphtha` | `modern_industrialization:naphtha`, `oritech:still_naphtha` |
| `sise_ores:biofuel` | `industrialforegoing:biofuel`, `modern_industrialization:biodiesel`, `oritech:still_biofuel` |
| `sise_ores:latex` | `cakesticklib:latex`, `industrialforegoing:latex` |
| `sise_ores:ethanol` | `mekanismgenerators:bioethanol`, `modern_industrialization:ethanol` |
| `sise_ores:molten_redstone` | `cakesticklib:molten_redstone`, `modern_industrialization:molten_redstone` |

## 已安装产物

- `SISE-Ores-0.6.0+26.1.2.jar`，SHA-256：`2ccdeed67f20a6a52b0a9cb40ccb62176a994c56e77faaf2f2002b5e6c089c7a`。
- `SISE-Compat-0.3.0+26.1.2.jar`，SHA-256：`174b92b90f883d7f8d5df69c3ee9ed5b68cb3330fbc9212970a3f01ee73129c2`。

旧 Ores／Compat JAR 已备份到 `.work/verification/sise-ores-fluids-20261016/backup`。重启游戏后生效；旧物品继续用于等价配方或手动迁移。不批量扫描库存或改写已有区块。
