"""Build the instance-specific SISE Ores mod without modifying upstream jars."""
from pathlib import Path
import collections
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = Path(os.environ.get('SISE_INSTANCE_DIR', HERE.parent.parent)).expanduser().resolve()
_bundled_jdk = ROOT / '.work/devtools/jdk-25'
_jdk = os.environ.get('SISE_JDK_DIR') or (_bundled_jdk if _bundled_jdk.is_dir() else os.environ.get('JAVA_HOME', _bundled_jdk))
JAVA = Path(_jdk).expanduser().resolve() / 'bin'
BUILD = HERE / 'build'
RES = BUILD / 'resources'
VERSION = '0.3.0+26.1.2'
MATERIALS = {
    'iron': ('Iron', '铁'), 'gold': ('Gold', '金'), 'copper': ('Copper', '铜'),
    'tin': ('Tin', '锡'), 'lead': ('Lead', '铅'), 'silver': ('Silver', '银'),
    'nickel': ('Nickel', '镍'), 'aluminum': ('Aluminum', '铝'),
    'platinum': ('Platinum', '铂'), 'titanium': ('Titanium', '钛'),
    'tungsten': ('Tungsten', '钨'), 'uranium': ('Uranium', '铀'),
    'bronze': ('Bronze', '青铜'), 'steel': ('Steel', '钢'),
    'electrum': ('Electrum', '琥珀金'), 'osmium': ('Osmium', '锇'),
    'silicon': ('Silicon', '硅'),
}
FORMS = {'ingots': ('ingot', 'Ingot', '锭'), 'nuggets': ('nugget', 'Nugget', '粒'),
         'dusts': ('dust', 'Dust', '粉'), 'storage_blocks': ('block', 'Block', '块'),
         'raw_materials': ('raw', 'Raw Ore', '粗矿'), 'gems': ('gem', 'Gem', '晶体'),
         'ores': ('ore', 'Ore', '矿石')}
PRIORITY = ['mekanism', 'mekmm', 'modern_industrialization', 'oritech', 'enderio', 'ltxi', 'neoecoae', 'iceandfire', 'cakesticklib']
EXCLUDED = {'oritech:biosteel_ingot'}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def inventory():
    if not (ROOT / 'mods').is_dir():
        raise FileNotFoundError('Set SISE_INSTANCE_DIR to the project02 instance containing mods/ and libraries/.')
    tags, files, fingerprints = collections.defaultdict(set), {}, {}
    for path in sorted((ROOT / 'mods').glob('*.jar')):
        if path.name.startswith('SISE-Ores-'):
            continue
        with zipfile.ZipFile(path) as archive:
            used = False
            for name in archive.namelist():
                if not name.startswith(('assets/', 'data/')) or not name.endswith('.json'):
                    continue
                try:
                    data = json.loads(archive.read(name))
                except (ValueError, UnicodeError):
                    continue
                if '/tags/' in name and name in files and isinstance(data, dict):
                    # Tag files merge across packs; retain all direct declarations for inheritance.
                    data = dict(data, values=files[name].get('values', []) + data.get('values', []))
                files[name] = data
                if name.startswith('data/c/tags/item/'):
                    tag = name[len('data/c/tags/item/'):-5]
                    for entry in data.get('values', []):
                        if isinstance(entry, str) and not entry.startswith('#'):
                            tags[tag].add(entry)
                    if tag.split('/')[-1] in MATERIALS:
                        used = True
            if used:
                fingerprints[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    return tags, files, fingerprints


def create_rules(tags, files):
    rules = []
    for material in MATERIALS:
        for form, (suffix, _, _) in FORMS.items():
            # Keep vanilla base metals in all vanilla forms. Only their dusts need unification.
            if material in ('iron', 'gold', 'copper') and form != 'dusts':
                continue
            tag = f'{form}/{material}'
            members = sorted(tags[tag] - EXCLUDED)
            if not members:
                continue
            members = [m for m in members if 'assets/' + m.replace(':', '/items/') + '.json' in files]
            if not members:
                raise ValueError(f'No registered item model candidates for {tag}')
            preferred = min(members, key=lambda s: (PRIORITY.index(s.split(':')[0]) if s.split(':')[0] in PRIORITY else 999, s))
            rules.append(dict(name=f'{material}_{suffix}', material=material, form=form,
                              preferred=preferred, members=members, tag=f'c:{tag}'))
    write(HERE / 'materials.json', {'version': 1, 'priority': PRIORITY, 'excluded': sorted(EXCLUDED), 'materials': rules})
    return rules


def resources():
    tags, files, fingerprints = inventory()
    rules_path = HERE / 'materials.json'
    rules = json.loads(rules_path.read_text('utf-8'))['materials'] if rules_path.exists() else create_rules(tags, files)
    write(RES / 'sise_ores/materials.json', {'materials': rules})
    write(HERE / 'verification/source-mods.json', fingerprints)
    languages = {'en_us': {'itemGroup.sise_ores': 'SISE Ores'}, 'zh_cn': {'itemGroup.sise_ores': 'SISE Ores'}}
    additions = collections.defaultdict(set)
    source_targets = {}
    preferred_blocks = {}
    ore_blocks = {}
    for rule in rules:
        for member in rule['members']:
            if member in source_targets:
                raise ValueError(f'Ambiguous member: {member}')
            source_targets[member] = rule.get('canonical', 'sise_ores:' + rule['name'])
    for rule in rules:
        name, material, form = rule['name'], rule['material'], rule['form']
        target = rule.get('canonical', 'sise_ores:' + name)
        source = rule['preferred']
        source_ns, source_name = source.split(':')
        item_model = files[f'assets/{source_ns}/items/{source_name}.json']
        if 'canonical' not in rule:
            write(RES / f'assets/sise_ores/items/{name}.json', item_model)
        for lang, mat_name in zip(('en_us', 'zh_cn'), MATERIALS[material]):
            suffix = FORMS[form][1 if lang == 'en_us' else 2]
            display = f'{mat_name} {suffix}' if lang == 'en_us' else mat_name + suffix
            if form == 'raw_materials': display = f'Raw {mat_name}' if lang == 'en_us' else '粗' + mat_name
            display = rule.get('display_en' if lang == 'en_us' else 'display_zh', display)
            languages[lang][f'{"block" if form in ("storage_blocks", "ores") else "item"}.sise_ores.{name}'] = display
        tag_path = rule['tag'].split(':')[1]
        additions[f'data/c/tags/item/{tag_path}.json'].add(target)
        additions[f'data/c/tags/item/{form}.json'].add(target)
        group = rule.get('group', tag_path)
        write(RES / f'data/sise_ores/tags/item/equivalents/{group}.json', {
            'replace': False, 'values': [target] + [{'id': m, 'required': False} for m in rule['members']]})
        # A one-way legacy conversion. The runtime transformer deliberately skips these recipes.
        for member in rule['members']:
            write(RES / f'data/sise_ores/recipe/convert/{name}/{member.replace(":", "/")}.json', {
                'type': 'minecraft:stonecutting',
                'neoforge:conditions': [{'type': 'neoforge:registered', 'value': member}],
                'ingredient': member, 'result': {'id': target}})
        if form in ('storage_blocks', 'ores'):
            model = item_model['model']
            if model['type'] != 'minecraft:model':
                raise ValueError(f'Unsupported source block model: {source}')
            write(RES / f'assets/sise_ores/blockstates/{name}.json', {'variants': {'': {'model': model['model']}}})
            if form == 'ores':
                # Preserve the priority source's complete fortune/silk/explosion logic.
                # Only rewrite reviewed item-entry identities, never predicates or other fields.
                loot = json.loads(json.dumps(files[f'data/{source_ns}/loot_table/blocks/{source_name}.json']))
                if source in ('mekmm:silver_ore', 'mekmm:deepslate_silver_ore'):
                    # These two upstream tables unconditionally drop themselves, despite having raw silver.
                    # Give the new canonical silver ore MEK's normal raw/silk/fortune behavior.
                    expected_pool = {'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                                     'entries': [{'type': 'minecraft:item', 'name': source}], 'name': 'main', 'rolls': 1.0}
                    if loot['pools'] != [expected_pool]: raise ValueError('Review updated silver ore loot: ' + source)
                    template_name = source_name.replace('silver', 'tin')
                    template = json.dumps(files[f'data/mekanism/loot_table/blocks/{template_name}.json'])
                    template = template.replace('mekanism:' + template_name, target).replace('mekanism:raw_tin', 'sise_ores:raw_silver')
                    loot = json.loads(template)
                    loot['random_sequence'] = f'sise_ores:blocks/{name}'
                def rewrite_entries(value):
                    if isinstance(value, dict):
                        if value.get('type') == 'minecraft:item' and value.get('name') in source_targets:
                            value['name'] = source_targets[value['name']]
                        for child in value.values(): rewrite_entries(child)
                    elif isinstance(value, list):
                        for child in value: rewrite_entries(child)
                rewrite_entries(loot)
                write(RES / f'data/sise_ores/loot_table/blocks/{name}.json', loot)
                additions[f'data/c/tags/block/ores_in_ground/{rule["group"].split("/")[-1]}.json'].add(target)
                additions[f'data/c/tags/item/ores_in_ground/{rule["group"].split("/")[-1]}.json'].add(target)
            else:
                write(RES / f'data/sise_ores/loot_table/blocks/{name}.json', {
                    'type': 'minecraft:block', 'pools': [{'rolls': 1,
                    'entries': [{'type': 'minecraft:item', 'name': target}],
                    'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
            additions[f'data/c/tags/block/{tag_path}.json'].add(target)
            additions[f'data/c/tags/block/{form}.json'].add(target)
            additions['data/minecraft/tags/block/mineable/pickaxe.json'].add(target)
            preferred_blocks[source] = target
            if form == 'ores':
                for member in rule['members']: ore_blocks[member] = target
    # Add new identities to every existing direct item tag for their equivalents.
    # Do not rewrite or flatten tag references, and never infer new equivalence from overlaps.
    for path, data in files.items():
        if not isinstance(data, dict):
            continue
        mining_tier = '/tags/block/needs_' in path or '/tags/block/incorrect_for_' in path
        inherited = source_targets if '/tags/item/' in path else (preferred_blocks if mining_tier else preferred_blocks | ore_blocks) if '/tags/block/' in path else {}
        if inherited:
            for entry in data.get('values', []):
                member = entry if isinstance(entry, str) else entry.get('id', '')
                if member in inherited:
                    additions[path].add(inherited[member])
    for path, values in additions.items():
        write(RES / path, {'replace': False, 'values': sorted(values)})
    # Oritech's custom generators use block IDs rather than OreConfiguration targets.
    # Keep the resource nodes/crystals and all frequencies/weights; change only the surrounding ores.
    for feature in ('resource_node_common', 'resource_node_rare', 'uranium_patch'):
        path = f'data/oritech/worldgen/configured_feature/{feature}.json'
        value = json.loads(json.dumps(files[path]))
        config = value['config']
        if 'boulderOres' in config:
            config['boulderOres'] = [ore_blocks.get(v, v) for v in config['boulderOres']]
        if 'blockId' in config: config['blockId'] = ore_blocks.get(config['blockId'], config['blockId'])
        write(RES / path, value)
    for lang, data in languages.items():
        write(RES / f'assets/sise_ores/lang/{lang}.json', data)
    write(RES / 'data/neoforge/loot_modifiers/global_loot_modifiers.json', {'replace': False, 'entries': ['sise_ores:materials']})
    write(RES / 'data/sise_ores/loot_modifiers/materials.json', {'type': 'sise_ores:materials', 'conditions': [], 'priority': 0})
    write(RES / 'META-INF/neoforge.mods.toml', f'''modLoader="javafml"
loaderVersion="[10,)"
license="MIT"
[[mods]]
modId="sise_ores"
version="{VERSION}"
displayName="SISE Ores"
description="Unified basic materials for project02. Mekanism-first appearance, explicit equivalence rules, recipe output unification."
[[mixins]]
config="sise_ores.mixins.json"
[[dependencies.sise_ores]]
modId="minecraft"
type="required"
versionRange="[26.1.2]"
side="BOTH"
[[dependencies.sise_ores]]
modId="neoforge"
type="required"
versionRange="[26.1.2.114,26.2)"
side="BOTH"
'''+''.join(f'''[[dependencies.sise_ores]]
modId="{mod}"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="BOTH"
''' for mod in sorted({r['preferred'].split(':')[0] for r in rules})))
    write(RES / 'sise_ores.mixins.json', {'required': True, 'minVersion': '0.8',
          'package': 'local.sise.ores.compat.mixin', 'compatibilityLevel': 'JAVA_25',
          'mixins': ['RaritySyncSnapshotMixin', 'ManualStonecuttingMixin', 'OreTargetMixin', 'WorldGenOreMixin'], 'injectors': {'defaultRequire': 1}})
    write(RES / 'LICENSE', 'SPDX-License-Identifier: MIT\nCopyright (c) 2026 SISE Ores contributors\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.\n\nModels and textures are referenced from installed mods; no upstream image assets are bundled.\n')
    print(f'Materials: {len(rules)}; legacy identities: {len(source_targets)}', flush=True)


def classpath():
    paths = list((ROOT / 'libraries').rglob('*.jar')) + list((ROOT / 'mods').glob('*.jar'))
    paths.sort(key=lambda p: (0 if 'minecraft-client-patched' in str(p) else 1, str(p)))
    return ';'.join(map(str, paths))


def compile_sources(source, output, extra=''):
    if not (JAVA / 'javac.exe').is_file():
        raise FileNotFoundError('JDK 25 not found; set SISE_JDK_DIR (or JAVA_HOME) to a Windows JDK 25 installation.')
    if not (ROOT / 'libraries').is_dir():
        raise FileNotFoundError('SISE_INSTANCE_DIR must contain the resolved NeoForge 26.1.2 libraries/.')
    output.mkdir(parents=True, exist_ok=True)
    args = ['-encoding', 'UTF-8', '--release', '25', '-proc:none', '-classpath',
            extra + classpath(), '-d', str(output)] + [str(p) for p in source.rglob('*.java')]
    argfile = BUILD / (output.name + '.args')
    write(argfile, '\n'.join('"' + s.replace('\\', '/') + '"' for s in args))
    subprocess.run([str(JAVA / 'javac.exe'), '@' + str(argfile)], check=True)


def build():
    # Only disposable outputs below this project's resolved build directory may be removed.
    for output in (BUILD / 'classes', RES):
        checked = output.resolve()
        if not checked.is_relative_to(BUILD.resolve()) or checked == BUILD.resolve():
            raise ValueError('Refusing to clean outside the SISE Ores build directory')
        if checked.exists():
            shutil.rmtree(checked)
    resources()
    classes = BUILD / 'classes'
    compile_sources(HERE / 'src/main/java', classes)
    out = BUILD / f'libs/SISE-Ores-{VERSION}.jar'
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for base in (classes, RES):
            for p in sorted(base.rglob('*')):
                if p.is_file():
                    z.write(p, p.relative_to(base).as_posix())
    print(out, flush=True)


if __name__ == '__main__':
    build()
