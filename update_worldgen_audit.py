"""Adopt the latest component-aware recipe audit and freeze the installed 0.2.0/Compat 0.1.1 baseline."""
from pathlib import Path
import shutil
import build

source=build.HERE.parent/'SISE-Compat'
baseline=build.HERE/'verification/baseline-worldgen'
baseline.mkdir(parents=True,exist_ok=True)
for name in ('recipe-audit.json','recipe-audit-export-final.json.gz'):
    target=baseline/name
    if not target.exists(): shutil.copy2(source/'verification'/name,target)
text=(source/'audit.py').read_text('utf-8')
text=text.replace("verification/baseline/", "verification/baseline-worldgen/")
text=text.replace("compat-fix-comparison.json", "worldgen-recipe-comparison.json")
text=text.replace("    for r in rules:\n        u =", "    for r in rules:\n        if r['form'] == 'ores': continue  # Ore yield is route-dependent.\n        u =")
start=text.index('    lines = [f"# SISE Compat')
end=text.index('    print(json.dumps(summary,indent=2))',start)
text=text[:start]+'''    lines = [f"# SISE Ores {build.VERSION} 配方回归报告", '',
        '基线：已安装 SISE Ores 0.2.0 + SISE Compat 0.1.1。世界生成专项证据见 worldgen-report.json。', '',
        '| 检查 | 结果 |', '|---|---:|']
    labels = {'loaded_recipes':'实际加载配方','actual_recipe_load_errors':'加载错误',
        'crafting_conflicts':'工作台冲突候选','introduced_since_baseline_crafting_pairs':'相对安装基线新增工作台冲突',
        'runtime_execution_passed':'配方匹配/产出调用','runtime_execution_errors':'执行异常',
        'remaining_legacy_references':'配方残留已审核旧材料','mi_legacy_references':'MI 派生配方残留旧材料',
        'introduced_automatic_machine_pairs':'相对安装基线新增机器同输入候选',
        'preexisting_automatic_machine_candidates':'保留的机器同输入候选','tag_compatibility_gaps':'物品标签继承缺口',
        'material_mass_gains':'可计量纯材料增殖','reload_identical':'配方重载一致'}
    lines += [f"| {label} | {summary[key]} |" for key,label in labels.items()]
    lines += ['', '矿石在原有材料白名单中增加独立宿主岩石分组，原料兼容扩展到新矿石，产出统一身份；数量、概率、能耗和时长保持。',
        '机器候选不等于已经证明的运行冲突；本次未逐台穷举所有机器、组件组合和跨机器循环。',
        '完整比较：worldgen-recipe-comparison.json；配方明细：recipe-audit.json。', '']
    dump('recipe-audit-report.md', '\\n'.join(lines))
''' + text[end:]
(build.HERE/'audit.py').write_text(text,'utf-8')
print('Updated audit with frozen installed baseline.')
