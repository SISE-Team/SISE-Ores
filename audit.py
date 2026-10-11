"""Audit the complete live recipe export; retain pre-existing findings separately."""
import collections
import itertools
import json
import gzip
import re
from pathlib import Path
import build
import verify


def dump(name, data):
    build.write(build.HERE / 'verification' / name, data)


def run():
    client = json.loads((build.HERE / 'verification/client-result.json').read_text('utf-8'))
    if not client.get('passed') or client.get('exit_code') != 0:
        raise RuntimeError('Complete client verification must pass before the final audit')
    data = json.loads((verify.FIXTURE / 'recipe-audit-export.json').read_text('utf-8'))
    after, before, tags = data['loaded'], {k: v for k, v in data['before'].items() if not k.startswith('sise_ores:')}, data['item_tags']
    rules = json.loads((build.HERE.parent / 'SISE-Ores/materials.json').read_text('utf-8'))['materials']
    build.MATERIALS = {r['material'] for r in rules}
    mapping = {s: r.get('canonical', 'sise_ores:' + r['name']) for r in rules for s in r['members']}

    def refs(x):
        if isinstance(x, str): return {x} & mapping.keys()
        if isinstance(x, dict): x = list(x.values())
        if isinstance(x, list): return set().union(*(refs(v) for v in x)) if x else set()
        return set()

    def canonical(x, baseline=False):
        if isinstance(x, str) and x.startswith('#') and x[1:] in tags:
            return sorted(v for v in tags[x[1:]] if not baseline or not v.startswith('sise_ores:'))
        if isinstance(x, list): return [canonical(v, baseline) for v in x]
        if isinstance(x, dict): return {k: canonical(v, baseline) for k, v in x.items()}
        return x

    def domain(x, baseline=False):
        if isinstance(x, str):
            values = tags.get(x[1:], []) if x.startswith('#') else [x]
            return frozenset(v for v in values if not baseline or not v.startswith('sise_ores:'))
        if isinstance(x, list):
            ds = [domain(v, baseline) for v in x]
            return frozenset.union(*ds) if ds and all(v is not None for v in ds) else None
        if isinstance(x, dict):
            if 'ingredient' in x: return domain(x['ingredient'], baseline)
            if x.get('neoforge:ingredient_type') == 'sise_compat:pristine': return domain(x['item'], baseline)
            if x.get('neoforge:ingredient_type') == 'neoforge:compound': return domain(x['children'], baseline)
            if x.get('neoforge:ingredient_type') == 'avaritia:tag': return domain('#' + x['tag'], baseline)
        return None

    def stable(x): return json.dumps(x, sort_keys=True, separators=(',', ':'))

    # These fields are outputs/costs, never matching constraints. All unknown fields remain.
    non_inputs = {'result', 'results', 'output', 'outputs', 'itemOutput', 'item_outputs', 'item_results',
                  'itemResults', 'fluid_outputs', 'fluidOutputs', 'main_output', 'secondary_output',
                  'item_output', 'chemical_output', 'fluid_output', 'left_output', 'right_output',
                  'energy', 'eu', 'duration', 'time', 'cookingtime', 'experience', 'group', 'category',
                  'show_notification', 'energy_cost', 'totalEnergy', 'input_energy', 'energyPerCycle'}
    output_fields = non_inputs - {'energy', 'eu', 'duration', 'time', 'cookingtime', 'experience',
        'group', 'category', 'show_notification', 'energy_cost', 'totalEnergy', 'input_energy', 'energyPerCycle'}

    def machine_groups(recipes, baseline=False):
        groups = collections.defaultdict(list)
        skipped = []
        for rid, r in recipes.items():
            if 'pattern' in r or 'ingredients' in r: continue
            out = {k: v for k, v in r.items() if k in output_fields}
            inp = {k: canonical(v, baseline) for k, v in r.items() if k not in non_inputs}
            if not out or len(inp) < 2:
                skipped.append(rid); continue
            for key in ('item_inputs', 'inputs', 'itemInputs', 'input_items', 'item_results'):
                if isinstance(inp.get(key), list): inp[key] = sorted(inp[key], key=stable)
            groups[stable(inp)].append((rid, stable(out)))
        return groups, skipped

    machine_conflicts, duplicates = [], []
    groups, unclassified = machine_groups(after)
    old_groups, _ = machine_groups(before, True)
    old_pairs = {tuple(sorted((a[0], b[0]))) for group in old_groups.values()
                 for a, b in itertools.combinations(group, 2) if a[1] != b[1]}
    for group in groups.values():
        if len(group) < 2: continue
        for a, b in itertools.combinations(group, 2):
            pair = tuple(sorted((a[0], b[0])))
            info = {'recipes': pair, 'type': after[a[0]]['type'], 'preexisting': pair in old_pairs,
                    'selectable': after[a[0]]['type'] in {'minecraft:stonecutting', 'modern_industrialization:forge_hammer', 'tacz:gun_smith_table_crafting', 'cookingforblockheads:kitchen'},
                    'baseline_available': a[0] in before and b[0] in before}
            (duplicates if a[1] == b[1] else machine_conflicts).append(info)

    # Pairwise domain intersection for normal crafting, with shaped mirroring and
    # bipartite matching for shapeless/mixed pairs. Explicit witness grids follow.
    craft_types = {'minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'mekanism:mek_data',
                   'enderio:shaped_entity_storage', 'functionalstorage:copy_components',
                   'sophisticatedcore:upgrade_next_tier', 'sophisticatedbackpacks:basic_backpack',
                   'sophisticatedbackpacks:backpack_upgrade', 'sophisticatedstorage:storage_tier_upgrade',
                   'sophisticatedstorage:storage_tier_upgrade_shapeless',
                   'sophisticatedstorage:shulker_box_from_chest', 'sophisticatedstorage:shulker_box_from_vanilla_shapeless'}

    def crafting(recipes, baseline=False):
        result, skipped = {}, []
        for rid, r in recipes.items():
            if r['type'] not in craft_types: continue
            if 'result' not in r: skipped.append(rid); continue
            if 'pattern' in r:
                pat = r['pattern'][:]
                while pat and not pat[0].strip(): pat.pop(0)
                while pat and not pat[-1].strip(): pat.pop()
                if not pat: skipped.append(rid); continue
                left = min(i for row in pat for i, c in enumerate(row) if c != ' ')
                right = max(i for row in pat for i, c in enumerate(row) if c != ' ')
                pat = [row[left:right+1] for row in pat]
                ds = [domain(r['key'][c], baseline) if c != ' ' else frozenset(['minecraft:air']) for row in pat for c in row]
                shape = (len(pat[0]), len(pat))
                count = sum(c != ' ' for row in pat for c in row)
            elif 'ingredients' in r and isinstance(r['ingredients'], list):
                ds = [domain(v, baseline) for v in r['ingredients']]; shape = None; count = len(ds)
            else: skipped.append(rid); continue
            if any(v is None or not v for v in ds): skipped.append(rid); continue
            result[rid] = (shape, ds, count, stable(r['result']))
        return result, skipped

    def overlap(a, b):
        sa, da, ca, _ = a; sb, db, cb, _ = b
        if ca != cb: return None
        if sa and sb:
            if sa != sb: return None
            w, h = sa
            for trial in [db, [db[y*w+w-1-x] for y in range(h) for x in range(w)]]:
                sets = [x & y for x, y in zip(da, trial)]
                if all(sets): return (w, h, [sorted(s)[0] for s in sets])
            return None
        if sb: return overlap(b, a)
        positions = [i for i, s in enumerate(da) if s != frozenset(['minecraft:air'])]
        edges = [[j for j, s in enumerate(db) if da[i] & s] for i in positions]
        if any(not e for e in edges): return None
        order = sorted(range(len(edges)), key=lambda i: len(edges[i])); assignment = {}
        def search(n, used):
            if n == len(order): return True
            i = order[n]
            for j in edges[i]:
                if j not in used:
                    assignment[i] = j
                    if search(n+1, used | {j}): return True
            return False
        if not search(0, set()): return None
        grid = ['minecraft:air'] * len(da)
        for i, p in enumerate(positions): grid[p] = sorted(da[p] & db[assignment[i]])[0]
        if sa: w, h = sa
        else:
            w = min(3, len(grid)); h = (len(grid) + w - 1)//w
            grid += ['minecraft:air'] * (w*h - len(grid))
        return w, h, grid

    crafts, craft_skipped = crafting(after); old_crafts, _ = crafting(before, True)
    by_count = collections.defaultdict(list)
    for rid, record in crafts.items(): by_count[record[2]].append((rid, record))
    craft_conflicts = []; comparisons = 0
    for group in by_count.values():
        for (aid, a), (bid, b) in itertools.combinations(group, 2):
            if a[3] == b[3]: continue
            comparisons += 1
            witness = overlap(a, b)
            if witness:
                preexisting = aid in old_crafts and bid in old_crafts and old_crafts[aid][3] != old_crafts[bid][3] and overlap(old_crafts[aid], old_crafts[bid]) is not None
                craft_conflicts.append({'recipes': [aid, bid], 'preexisting': preexisting,
                    'width': witness[0], 'height': witness[1], 'items': witness[2],
                    'outputs': [json.loads(a[3]), json.loads(b[3])]})

    legacy = {k: sorted(refs(v)) for k, v in after.items() if not k.startswith('sise_ores:') and refs(v)}
    mi_legacy = {k: sorted(refs(v)) for k, v in data.get('mi_effective_recipes', {}).items() if refs(v)}
    tag_gaps = {tag: sorted({mapping[s] for s in values if s in mapping} - set(values))
                for tag, values in tags.items() if any(s in mapping and mapping[s] not in values for s in values)}
    # Compare conservative material mass only where every input/output is a known form
    # of one element. Does not assume arbitrary alloys, catalysts or ore yields are equivalent.
    units = {}
    for r in rules:
        if r['form'] == 'ores': continue  # Ore yield depends on the processing route; not fixed metal mass.
        u = {'ingots': 9, 'dusts': 9, 'nuggets': 1, 'storage_blocks': 81, 'raw_materials': 9, 'gems': 9}[r['form']]
        for s in r['members'] + [r.get('canonical', 'sise_ores:' + r['name'])]: units[s] = (r.get('unit_material', r['material']), u)
    for material in build.MATERIALS:
        for s in ['modern_industrialization:' + material + '_tiny_dust', 'oritech:small_' + material + '_dust']:
            if s in data['items']: units[s] = (material, 1)
    for material in ['iron', 'gold', 'copper']:
        for suffix, u in [('ingot', 9), ('nugget', 1), ('block', 81)]: units['minecraft:' + material + '_' + suffix] = (material, u)
    mass_checked, gains = [], []
    for rid, r in after.items():
        if any(k in r for k in ('fluid_inputs', 'fluidInput', 'chemical_input')): continue
        ins = []
        if 'pattern' in r and 'key' in r: ins = [r['key'][c] for row in r['pattern'] for c in row if c != ' ']
        else:
            for k in ('ingredient', 'ingredients', 'input', 'inputs', 'item_inputs', 'itemInputs'):
                if k in r: ins = r[k] if isinstance(r[k], list) else [r[k]]; break
        outs = []
        for k in ('result', 'output', 'outputs', 'item_outputs', 'item_results', 'itemResults'):
            if k in r: outs = r[k] if isinstance(r[k], list) else [r[k]]; break
        values, output_values = [], []
        for ing in ins:
            ds = domain(ing)
            us = {units.get(v) for v in ds} if ds else {None}
            if None in us or len(us) != 1: break
            mat, u = next(iter(us)); count = ing.get('amount', ing.get('count', 1)) if isinstance(ing, dict) else 1
            values.append((mat, u * count))
        if not ins or len(values) != len(ins): continue
        for out in outs:
            prob = out.get('chance', out.get('probability', 1)) if isinstance(out, dict) else 1
            if isinstance(out, dict) and 'item' in out: out = out['item']
            oid = out.get('id') if isinstance(out, dict) else out
            if oid not in units: break
            mat, u = units[oid]; count = out.get('amount', out.get('count', 1)) if isinstance(out, dict) else 1
            output_values.append((mat, u * count * prob))
        if not outs or len(output_values) != len(outs) or len({v[0] for v in values + output_values}) != 1: continue
        mass_checked.append(rid)
        a, b = sum(v[1] for v in values), sum(v[1] for v in output_values)
        if b > a + 0.00001: gains.append({'recipe': rid, 'input_units': a, 'expected_output_units': b})
    log = (verify.FIXTURE / 'logs/debug.log').read_text('utf-8', errors='replace')
    actual_errors = {}
    for line in log.splitlines():
        if "Couldn't parse data file" in line:
            match = re.search(r"Couldn't parse data file '([^']+)'", line)
            if match and match[1] in data['raw_before']: actual_errors[match[1]] = line.split('DataResult.Error', 1)[-1]
    summary = {'loaded_recipes': len(after), 'serializer_types': len({r['type'] for r in after.values()}),
        'encode_errors': len(data['encode_errors']), 'remaining_legacy_references': len(legacy),
        'actual_recipe_load_errors': len(actual_errors),
        'crafting_recipes_domain_checked': len(crafts), 'crafting_pair_comparisons': comparisons,
        'crafting_conflicts': len(craft_conflicts), 'new_crafting_conflicts': sum(not x['preexisting'] for x in craft_conflicts),
        'machine_exact_input_conflicts': len(machine_conflicts),
        'new_machine_exact_input_conflicts': sum(not x['preexisting'] and not x['selectable'] and x['baseline_available'] for x in machine_conflicts),
        'tag_compatibility_gaps': len(tag_gaps), 'material_mass_recipes_checked': len(mass_checked), 'material_mass_gains': len(gains)}
    execution = data.get('execution', {})
    summary.update({'runtime_execution_passed': sum(execution.get('passed_by_family', {}).values()),
        'runtime_execution_errors': len(execution.get('failures', {})),
        'runtime_crafting_conflicts_confirmed': len(execution.get('confirmed_crafting_conflicts', [])),
        'mi_effective_recipes': len(data.get('mi_effective_recipes', {})), 'mi_legacy_references': len(mi_legacy),
        'reload_identical': data.get('reload_identical', False)})
    previous = json.loads((build.HERE / 'verification/baseline-worldgen/recipe-audit.json').read_text('utf-8'))
    old_conflicts = {tuple(sorted(x['recipes'])) for x in previous['crafting_conflicts']}
    current_conflicts = {tuple(sorted(x['recipes'])) for x in craft_conflicts}
    comparison = {'restored_recipes': sorted(set(previous['recipe_load_errors']) & set(after)),
                  'resolved_crafting_pairs': [list(p) for p in sorted(old_conflicts - current_conflicts)],
                  'introduced_crafting_pairs': [list(p) for p in sorted(current_conflicts - old_conflicts)]}
    summary['restored_compat_recipes'] = len(comparison['restored_recipes'])
    summary['resolved_previous_crafting_pairs'] = len(comparison['resolved_crafting_pairs'])
    summary['introduced_since_baseline_crafting_pairs'] = len(comparison['introduced_crafting_pairs'])
    dump('worldgen-recipe-comparison.json', comparison)
    dump('recipe-audit.json', {'summary': summary, 'recipe_load_errors': actual_errors, 'legacy_references': legacy,
        'crafting_conflicts': craft_conflicts, 'machine_conflicts': machine_conflicts, 'equivalent_machine_duplicates': duplicates,
        'crafting_unclassified': craft_skipped, 'machine_unclassified': unclassified,
        'tag_compatibility_gaps': tag_gaps, 'material_mass_gains': gains, 'material_mass_checked': mass_checked,
        'runtime_execution': execution, 'mi_legacy_references': mi_legacy,
        'type_counts': dict(collections.Counter(r['type'] for r in after.values()))})
    dump('crafting-witnesses.json', craft_conflicts)
    summary['disabled_missing_item_recipes'] = ['avaritia:eio_creative_power'] if 'avaritia:eio_creative_power' not in after else []
    previous_export = json.load(gzip.open(build.HERE/'verification/baseline-worldgen/recipe-audit-export-final.json.gz', 'rt', encoding='utf-8'))
    comparison['all_previous_recipe_ids_retained_except_missing_item'] = not (set(previous_export['loaded']) - set(after))
    comparison['changed_loaded_recipe_ids'] = sorted(k for k in previous_export['loaded'] if k in after and previous_export['loaded'][k] != after[k])
    comparison['new_recipe_ids'] = sorted(set(after) - set(previous_export['loaded']))
    old_machine_pairs = {tuple(sorted(v['recipes'])) for v in previous['machine_conflicts'] if not v['selectable']}
    machine_pairs = {tuple(sorted(v['recipes'])) for v in machine_conflicts if not v['selectable']}
    comparison['resolved_automatic_machine_pairs'] = [list(p) for p in sorted(old_machine_pairs-machine_pairs)]
    comparison['introduced_automatic_machine_pairs'] = [list(p) for p in sorted(machine_pairs-old_machine_pairs)]
    summary['preexisting_automatic_machine_candidates'] = len(machine_pairs)
    summary['introduced_automatic_machine_pairs'] = len(comparison['introduced_automatic_machine_pairs'])
    summary['resolved_automatic_machine_pairs'] = len(comparison['resolved_automatic_machine_pairs'])
    dump('worldgen-recipe-comparison.json',comparison)
    report_data = json.loads((build.HERE/'verification/recipe-audit.json').read_text('utf-8'))
    report_data['summary'] = summary
    dump('recipe-audit.json',report_data)
    with gzip.open(build.HERE/'verification/recipe-audit-export-final.json.gz','wt',encoding='utf-8') as output:
        json.dump(data,output,ensure_ascii=False)
    lines = [f"# SISE Ores {build.VERSION} 配方回归报告", '',
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
    dump('recipe-audit-report.md', '\n'.join(lines))
    print(json.dumps(summary,indent=2))

if __name__ == '__main__': run()
