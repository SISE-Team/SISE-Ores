"""Collect evidence only after the complete isolated game run succeeds."""
import hashlib
import json
import shutil
import build
import verify

def main():
    out=build.HERE/'verification'
    client=json.loads((out/'client-result.json').read_text('utf-8'))
    if not client.get('passed') or client.get('exit_code')!=0: raise RuntimeError('Full client test must pass')
    report=json.loads((verify.FIXTURE/'worldgen-report.json').read_text('utf-8'))
    if not report.get('passed'): raise RuntimeError('Worldgen tests must pass')
    jar=build.BUILD/f'libs/SISE-Ores-{build.VERSION}.jar'
    artifact_hash=hashlib.sha256(jar.read_bytes()).hexdigest()
    if artifact_hash!=hashlib.sha256((verify.FIXTURE/'mods'/jar.name).read_bytes()).hexdigest(): raise RuntimeError('Changed artifact after testing')
    report['artifact_sha256']=artifact_hash
    report['full_client_assertions']=client['probe']['assertions']
    report['static_assertions']=json.loads((out/'transform-tests.json').read_text('utf-8'))['assertions']
    build.write(out/'worldgen-report.json',report)
    shutil.copy2(verify.FIXTURE/'screenshots/sise-ores-materials.png',out/'sise-ores-worldgen.png')
    rules=[r for r in json.loads((build.HERE/'materials.json').read_text('utf-8'))['materials'] if r['form']=='ores']
    lines=[f'# SISE Ores {build.VERSION} 世界生成验证','',
        '在含 SISE Compat 0.1.1 的完整 208 模组环境中启动独立客户端、新建普通地形世界；没有读取或修改玩家存档。',
        '统一 10 种金属的 35 个旧矿石 ID，注册 20 个 SISE 矿石版本。石头、深板岩、末地石分开；原版及其他特殊矿物保持原身份。','',
        '| 检查 | 结果 |','|---|---:|',
        f"| 全部有效生成配置编码与旧目标排查 | {report['configured_features_checked']} |",
        f"| 对照原文件、确认仅更换矿石身份的生成配置 | {report['changed_configured_features']} |",
        f"| 主世界实际生成区块扫描 | {report['overworld_chunks_scanned']} |",
        f"| 末地实际生成区块扫描 | {report['end_chunks_scanned']} |",
        f"| 扫描区块中残留本次白名单旧矿石 | {report['legacy_ore_blocks_in_scanned_chunks']} |",
        f"| 真实 OreFeature 矿脉和 WorldGenRegion 放置 | {len(report['actual_vein_placements'])} / 35 通过 |",
        f"| 普通挖掘、精准采集、采掘等级 | {len(report['mining'])} / 20 通过 |",
        '| 时运 III | 20 种矿石各 48 次，身份、数量范围与增产通过 |',
        f"| 世界生成专项断言 | {report['assertions']} |",
        f"| 完整客户端、配方、SISE Compat 和重载断言 | {report['full_client_assertions']} |",
        f"| 静态配方转换回归断言 | {report['static_assertions']} |",'',
        '## 实际自然生成抽样','',
        '区块扫描发生在测试人工放矿之前。抽样不是全部维度和全部地形的穷举；没有把人工放置的测试矿脉算入自然生成数量。','',
        '| 维度 | 矿石 ID | 数量 |','|---|---|---:|']
    for dimension,key in [('主世界','natural_overworld_ores'),('末地','natural_end_ores')]:
        for ore,count in report[key].items(): lines.append(f'| {dimension} | `{ore}` | {count} |')
    lines+=['','## 优先级与外观','',
        'Mekanism → Mekanism More Machine → Modern Industrialization → 其他模组。对应物品来源均为 SISE Ores。','',
        '| 统一矿石 | 参考来源 |','|---|---|']
    lines += [f"| {r['display_zh']} | `{r['preferred']}` |" for r in rules]
    lines += ['','## 本轮发现并修复','',
        '- 10 条熔炼／高炉配方的物品备选列表在换成标签后需使用 compound ingredient。保持同一原料槽任选一种矿石、产量、经验与烧制时间。',
        '- MEK More Machine 两种银矿原掉落表无条件掉自身。新 SISE 银矿按 MEK 标准掉粗银，精准采集掉矿石，时运生效；外观、硬度及工具等级保持优先来源。',
        '- Oritech 3 个自定义生成配置单独统一周围矿石和铀矿斑，资源节点本体、晶簇、油层与权重保留。','',
        '## 生效与边界','',
        '- 安装后重启游戏，在新生成区块生效；未自动替换已生成区块或容器库存，也未开启补矿。',
        '- 保留原有高度、维度、生物群系、矿脉规模、频率与暴露概率；没有关闭原有生成器或重新平衡总矿量。',
        '- 旧矿石可继续加工，新增 35 条切石机 1:1 手动转换。原版矿石、铝土矿、晶质铀矿、充能晶体和粗矿簇不混并。',
        '- 新矿石的源配方兼容与机器配方回归详见 recipe-audit-report.md；没有声称所有机器经济循环均完成穷举。','',
        f'测试 JAR SHA-256：`{artifact_hash}`','',
        '![游戏中统一矿石外观](sise-ores-worldgen.png)','']
    build.write(out/'worldgen-report.md','\n'.join(lines))
    print(json.dumps({k:report[k] for k in ('passed','assertions','configured_features_checked','changed_configured_features','full_client_assertions','artifact_sha256')},indent=2))

if __name__=='__main__': main()
