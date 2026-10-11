"""Read-only inventory of installed ore declarations and generation resources."""
import collections
import json
import zipfile
import build

def main():
    tags, files, fingerprints = build.inventory()
    groups = {k: sorted(v) for k, v in tags.items() if k.startswith('ores/')}
    features = {}
    for path, value in files.items():
        if '/worldgen/configured_feature/' in path:
            features[path] = value
    report = {'ore_item_tags': groups, 'features': features}
    build.write(build.HERE / 'verification/worldgen-inventory.json', report)
    print('Worldgen inventory:', len(groups), 'ore tags;', len(features), 'configured-feature resources')

if __name__ == '__main__': main()
