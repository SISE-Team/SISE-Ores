# SISE Ores 0.4.0 / SISE Compat 0.2.5 验证记录

2026-10-14，针对本实例全部 222 个模组 JAR 扫描公共标签、英文／中文显示名、实际注册表与配方。最终正式产物已安装，旧版本已备份。

34 种材料、137 个等价组、135 个 SISE 注册物品、343 个来源 ID；相对 0.3.1 补齐 122 个来源 ID，其中 IC2CE 共纳入 47 个 ID（含锡、铅、铀的 6 个矿石版本）。铜锭、铜粒保留原版身份。

## 工作台合成方式

七条 Oritech 普通整粉拆小撮粉恢复原有直接工作台合成，统一后的产物身份和 1:9 数量一致。硫粉拆小撮粉、IC2 铀块拆铀-238 使用材料加 IC2 锻造锤；锤子返还，损耗 1 点耐久，最后一次使用会正常损坏。原材料、产量、核材料 ID 和加工阶段均保留。

审计中 14 组原有工作台冲突通过 15 条明确规则区分：IC2 青铜装备、碳纤维、青铜粉和 CakestickLib 铁／金齿轮增加可返还的锻造锤；Extended Crafting 红石锭改为竖排铁锭、红石粉，ProjectE 中级共价粉改为横排。切石机继续用于旧材料 ID 的手动迁移，不作为上述冲突的处理方式。

## 实测结果

| 检查 | 结果 |
|---|---:|
| 完整客户端及资源重载断言 | 19196 通过 |
| 静态材料转换断言 | 8,444 通过 |
| 103 条配方来源保护断言 | 310 通过 |
| 材料冲突规则断言 | 36 通过 |
| 实际加载配方 | 18894 |
| 工作台输入域检查 | 8985 条，9002339 对比较 |
| 工作台冲突候选及实际确认冲突 | 0 / 0 |
| 配方加载／编码错误、遗留材料产出引用、公共标签缺口 | 0 |
| 重载前后完整配方注册表 | 一致 |
| 自动机器新增同输入冲突候选 | 0 |
| 纯材料守恒检查 | 999 条，新增材料增殖 0 |
| IC2CE 本体机器配方与加工 API | 280 条机器配方审核、161 项统一材料输入、工作台匹配及重载通过 |
| 矿石生成与挖掘 | 41 个来源、20 个宿主版本，2,054 项专项断言通过 |
| 普通地形自然生成区块扫描 | 主世界 25、末地 9；白名单旧矿石残留 0 |
| 生成配置 | 588 项编码检查，43 项仅替换矿石身份 |
| 其余模组与受保护游戏文件 | 220 个模组 JAR、106 个游戏文件哈希保持 |

注册、旧材料 1:1 转换、方块掉落、优先来源燃料值、压块与拆分、普通挖掘／精准采集／时运、JEI 来源、锤子返还与耐久、IC2 API 实际取料和配方重载都在独立测试世界中验证。测试探针不包含在正式 JAR，也没有读取玩家存档。

## 有意保留的区别

- 煤粉与碳粉分组；公共标签重叠不代表等价。
- 普通钢与生物钢、普通硅与硅锭路线分组。
- 小撮粉末按 1/9 单位单独归并；不与整粉 1:1 转换。
- MEK 盐地形块含 4 份盐，MI／豆腐工艺盐储存块含 9 份，保持不同身份。
- Oritech 铀粉块与普通铀锭块分开；IC2 同位素、核燃料、碎矿、纯净碎矿与铱碎片保持原加工含义。
- 装备、齿轮、机器、带颜色的鳞块和不同豆腐结构不因同名而归并。
- 保留 73 对原有自动机器同输入候选。本轮无新增，没有声称所有机器经济循环已穷举；纯材料守恒结果只覆盖可确定单位的配方。

## 新纳入的来源 ID

| 统一目标 | 本轮新增来源 |
|---|---|
| `sise_ores:iron_dust` | `ic2:iron_dust` |
| `sise_ores:gold_dust` | `ic2:gold_dust` |
| `sise_ores:copper_dust` | `ic2:copper_dust` |
| `sise_ores:tin_ingot` | `ic2:tin_ingot` |
| `sise_ores:tin_dust` | `ic2:tin_dust` |
| `sise_ores:tin_block` | `ic2:tin_block` |
| `sise_ores:lead_ingot` | `ic2:lead_ingot` |
| `sise_ores:lead_dust` | `ic2:lead_dust` |
| `sise_ores:lead_block` | `ic2:lead_block` |
| `sise_ores:silver_ingot` | `ic2:silver_ingot` |
| `sise_ores:silver_dust` | `ic2:silver_dust` |
| `sise_ores:silver_block` | `ic2:silver_block` |
| `sise_ores:uranium_ingot` | `ic2:uranium_ingot` |
| `sise_ores:uranium_block` | `ic2:uranium_block` |
| `sise_ores:bronze_ingot` | `ic2:bronze_ingot` |
| `sise_ores:bronze_dust` | `ic2:bronze_dust` |
| `sise_ores:bronze_block` | `ic2:bronze_block` |
| `sise_ores:steel_ingot` | `ic2:steel_ingot` |
| `sise_ores:steel_block` | `ic2:steel_block` |
| `sise_ores:raw_tin` | `ic2:raw_tin` |
| `sise_ores:raw_tin_block` | `ic2:raw_tin_block` |
| `sise_ores:raw_lead` | `ic2:raw_lead` |
| `sise_ores:raw_lead_block` | `ic2:raw_lead_block` |
| `sise_ores:raw_uranium` | `ic2:raw_uranium` |
| `sise_ores:raw_uranium_block` | `ic2:raw_uranium_block` |
| `sise_ores:deepslate_tin_ore` | `ic2:deepslate_tin_ore` |
| `sise_ores:tin_ore` | `ic2:tin_ore` |
| `sise_ores:deepslate_lead_ore` | `ic2:deepslate_lead_ore` |
| `sise_ores:lead_ore` | `ic2:lead_ore` |
| `sise_ores:deepslate_uranium_ore` | `ic2:deepslate_uranium_ore` |
| `sise_ores:uranium_ore` | `ic2:uranium_ore` |
| `minecraft:copper_ingot` | `ic2:copper_ingot` |
| `sise_ores:carbon_dust` | `cakesticklib:carbon_dust`, `ltxi:carbon_dust`, `modern_industrialization:carbon_dust` |
| `sise_ores:coal_dust` | `enderio:powdered_coal`, `ic2:coal_dust`, `mekanism:dust_coal`, `modern_industrialization:coal_dust`, `oritech:coal_dust` |
| `sise_ores:diamond_dust` | `appflux:diamond_dust`, `cakesticklib:diamond_dust`, `ic2:diamond_dust`, `mekanism:dust_diamond`, `modern_industrialization:diamond_dust` |
| `sise_ores:emerald_dust` | `appflux:emerald_dust`, `cakesticklib:emerald_dust`, `mekanism:dust_emerald`, `modern_industrialization:emerald_dust` |
| `sise_ores:ender_pearl_dust` | `ae2:ender_dust`, `enderio:powdered_ender_pearl` |
| `sise_ores:lapis_dust` | `cakesticklib:lapis_dust`, `enderio:powdered_lapis_lazuli`, `ic2:lapis_dust`, `mekanism:dust_lapis_lazuli`, `modern_industrialization:lapis_dust` |
| `sise_ores:obsidian_dust` | `enderio:powdered_obsidian`, `ic2:obsidian_dust`, `mekanism:dust_obsidian` |
| `sise_ores:plutonium_dust` | `modern_industrialization:plutonium_dust`, `oritech:plutonium_dust` |
| `sise_ores:quartz_dust` | `ae2cs:nether_quartz_dust`, `cakesticklib:quartz_dust`, `enderio:powdered_quartz`, `mekanism:dust_quartz`, `modern_industrialization:quartz_dust`, `oritech:quartz_dust` |
| `sise_ores:sodium_dust` | `ltxi:sodium_dust`, `modern_industrialization:sodium_dust` |
| `sise_ores:sulfur_dust` | `cakesticklib:sulfur_dust`, `ic2:sulfur_dust`, `ltxi:sulfur_dust`, `mekanism:dust_sulfur`, `modern_industrialization:sulfur_dust` |
| `sise_ores:wood_dust` | `cakesticklib:sawdust`, `mekanism:sawdust` |
| `sise_ores:lithium_dust` | `ic2:lithium_dust`, `mekanism:dust_lithium` |
| `sise_ores:salt_dust` | `mekanism:salt`, `modern_industrialization:salt_dust`, `tofucraft:salt` |
| `sise_ores:raw_silicon` | `cakesticklib:raw_silicon`, `oritech:raw_silicon` |
| `sise_ores:wrought_iron_ingot` | `cakesticklib:wrought_iron_ingot`, `twilightforest:wrought_iron_bar` |
| `sise_ores:wrought_iron_nugget` | `cakesticklib:wrought_iron_nugget` |
| `sise_ores:wrought_iron_block` | `cakesticklib:wrought_iron_block` |
| `sise_ores:charcoal_block` | `justdirethings:charcoal`, `mekanism:block_charcoal` |
| `sise_ores:salt_block` | `modern_industrialization:salt_block`, `tofucraft:salt_block` |
| `sise_ores:iron_tiny_dust` | `ic2:small_iron_dust`, `modern_industrialization:iron_tiny_dust`, `oritech:small_iron_dust` |
| `sise_ores:gold_tiny_dust` | `ic2:small_gold_dust`, `modern_industrialization:gold_tiny_dust`, `oritech:small_gold_dust` |
| `sise_ores:copper_tiny_dust` | `ic2:small_copper_dust`, `modern_industrialization:copper_tiny_dust`, `oritech:small_copper_dust` |
| `sise_ores:tin_tiny_dust` | `ic2:small_tin_dust`, `modern_industrialization:tin_tiny_dust` |
| `sise_ores:lead_tiny_dust` | `ic2:small_lead_dust`, `modern_industrialization:lead_tiny_dust` |
| `sise_ores:silver_tiny_dust` | `ic2:small_silver_dust`, `modern_industrialization:silver_tiny_dust` |
| `sise_ores:bronze_tiny_dust` | `ic2:small_bronze_dust`, `modern_industrialization:bronze_tiny_dust` |
| `sise_ores:sulfur_tiny_dust` | `ic2:small_sulfur_dust`, `modern_industrialization:sulfur_tiny_dust` |
| `sise_ores:lapis_tiny_dust` | `ic2:small_lapis_dust`, `modern_industrialization:lapis_tiny_dust` |
| `sise_ores:nickel_tiny_dust` | `modern_industrialization:nickel_tiny_dust`, `oritech:small_nickel_dust` |
| `sise_ores:platinum_tiny_dust` | `modern_industrialization:platinum_tiny_dust`, `oritech:small_platinum_dust` |
| `sise_ores:uranium_tiny_dust` | `modern_industrialization:uranium_tiny_dust`, `oritech:small_uranium_dust` |
| `sise_ores:plutonium_tiny_dust` | `modern_industrialization:plutonium_tiny_dust`, `oritech:small_plutonium_dust` |
| `sise_ores:flour` | `ae2cs:flour`, `cakesticklib:flour` |

## 安装产物

- `SISE-Ores-0.4.0+26.1.2.jar`：`c795ee3829d6508b519a58fa1fdf12b64f3ac7c22f3b94bf79bd4012fd8880cf`
- `SISE-Compat-0.2.5+26.1.2.jar`：`80be29452ecf45ac0c5cf96522de90a84ad88262c44acc9bc53e5fa3ab0e9d59`

备份在实例 `.work/verification/sise-ores-complete-20261014/backup/`。游戏本体、IC2CE 发行 JAR 和其余模组未修改；重启后生效，新生成矿石在新生成区块中使用统一 ID。已有库存与区块不自动改写。

完整 JSON、运行日志、实际配方导出和截图位于本地忽略的 `verification/`，客户端测试实例为 `.work/test-sise-ores-crafting-20261014`。此记录对应最终安装 JAR 的 SHA-256，历史中间版本不作为最终证据。
