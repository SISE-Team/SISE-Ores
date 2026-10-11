"""One-time, reviewed extension of the existing material whitelist to metal ores."""
import collections
import json
import build

METALS = ['tin', 'lead', 'silver', 'nickel', 'aluminum', 'platinum', 'titanium', 'tungsten', 'uranium', 'osmium']

def main():
    tags, files, _ = build.inventory()
    catalog = json.loads((build.HERE / 'materials.json').read_text('utf-8'))
    rules = [r for r in catalog['materials'] if r['form'] != 'ores']
    additions = []
    for metal in METALS:
        variants = collections.defaultdict(list)
        for item in sorted(tags['ores/' + metal]):
            path = item.split(':')[1]
            if path == metal + '_ore': host = 'stone'
            elif path == 'deepslate_' + metal + '_ore': host = 'deepslate'
            elif path == 'endstone_' + metal + '_ore': host = 'end_stone'
            else: raise ValueError('Unreviewed ore variant: ' + item)
            variants[host].append(item)
        for host, members in variants.items():
            preferred = min(members, key=lambda s: (build.PRIORITY.index(s.split(':')[0]), s))
            prefix = {'stone': '', 'deepslate': 'deepslate_', 'end_stone': 'endstone_'}[host]
            rule = dict(name=prefix + metal + '_ore', material=metal, form='ores', preferred=preferred,
                        members=members, tag='c:ores/' + metal, group='ores/' + metal + '/' + host,
                        display_en={'stone': '', 'deepslate': 'Deepslate ', 'end_stone': 'End Stone '}[host] + build.MATERIALS[metal][0] + ' Ore',
                        display_zh={'stone': '', 'deepslate': '深层', 'end_stone': '末地石'}[host] + build.MATERIALS[metal][1] + '矿石')
            additions.append(rule)
    catalog['version'] = 3
    catalog['materials'] = rules + additions
    build.write(build.HERE / 'materials.json', catalog)
    print('Reviewed ore groups:', len(additions), 'legacy blocks:', sum(len(r['members']) for r in additions))
    for r in additions: print(r['name'], '<-', r['preferred'], r['members'])

if __name__ == '__main__': main()
