"""Install only the exact SISE Ores artifact that passed the isolated full-pack test."""
import hashlib
import json
import shutil
import build


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    verification = build.HERE / 'verification'
    result = json.loads((verification / 'client-result.json').read_text('utf-8'))
    if not result.get('passed') or result.get('exit_code') != 0:
        raise RuntimeError('Full-pack integration test has not passed')
    audit = json.loads((verification / 'recipe-audit.json').read_text('utf-8'))['summary']
    for field in ('encode_errors', 'remaining_legacy_references', 'new_crafting_conflicts', 'actual_recipe_load_errors', 'crafting_conflicts',
                  'new_machine_exact_input_conflicts', 'tag_compatibility_gaps', 'material_mass_gains',
                  'runtime_execution_errors', 'mi_legacy_references'):
        if audit.get(field) != 0: raise RuntimeError('Audit regression: ' + field)
    if audit.get('introduced_since_baseline_crafting_pairs') != 0 or audit.get('introduced_automatic_machine_pairs') != 0:
        raise RuntimeError('Recipe regression against the installed Ores 0.2.0 / Compat 0.1.1 baseline')
    worldgen = json.loads((verification / 'worldgen-report.json').read_text('utf-8'))
    if not worldgen.get('passed') or worldgen.get('legacy_ore_blocks_in_scanned_chunks') != 0 or len(worldgen.get('mining', {})) != 20 or len(worldgen.get('actual_vein_placements', {})) != 35:
        raise RuntimeError('World generation / ore mining tests have not passed')
    if audit.get('reload_identical') is not True: raise RuntimeError('Full recipe reload consistency not verified')
    source = build.BUILD / f'libs/SISE-Ores-{build.VERSION}.jar'
    import verify
    fixture = verify.FIXTURE / 'mods' / source.name
    if sha(source) != sha(fixture):
        raise RuntimeError('Artifact differs from the tested fixture')
    destination = build.ROOT / 'mods' / source.name
    existing = list((build.ROOT / 'mods').glob('SISE-Ores-*.jar'))
    if existing and not (existing == [destination] and sha(destination) == sha(source)):
        record = verification / 'installation.json'
        previous = json.loads(record.read_text('utf-8')) if record.exists() else {}
        if len(existing) != 1 or previous.get('sha256') != sha(existing[0]) or previous.get('installed') != str(existing[0]):
            raise RuntimeError('Existing SISE Ores does not match this task installation record')
        previous_jar = existing[0]
        backup = build.HERE / 'backup' / (previous_jar.stem + '-' + sha(previous_jar)[:12] + '.jar')
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(previous_jar, backup)
    before = {p.name: sha(p) for p in (build.ROOT / 'mods').glob('*.jar') if p not in existing and p != destination}
    core_paths = [build.ROOT / 'project02.jar', build.ROOT / 'project02.json']
    core_paths += list((build.ROOT / 'libraries/net/neoforged/minecraft-client-patched/26.1.2.114').glob('*.jar'))
    core_before = {str(p.relative_to(build.ROOT)): sha(p) for p in core_paths}
    previous_record = json.loads((verification / 'installation.json').read_text('utf-8'))
    if previous_record.get('game_files_unchanged') != core_before:
        raise RuntimeError('Core files differ from the previous verified installation')
    shutil.copy2(source, destination)
    for old in existing:
        if old != destination:
            if old.resolve().parent != (build.ROOT / 'mods').resolve(): raise RuntimeError('Invalid old artifact path')
            old.unlink()
    after = {p.name: sha(p) for p in (build.ROOT / 'mods').glob('*.jar') if p != destination}
    if before != after or any(sha(build.ROOT / p) != h for p, h in core_before.items()):
        raise RuntimeError('Unexpected changes to existing mods or game files')
    report = {'installed': str(destination), 'sha256': sha(destination), 'bytes': destination.stat().st_size,
              'existing_mods_unchanged': len(before), 'game_files_unchanged': core_before,
              'tested_assertions': result['probe']['assertions'], 'worldgen_assertions': worldgen['assertions'],
              'ore_variants': len(worldgen['mining']), 'legacy_ore_identities': len(worldgen['actual_vein_placements']),
              'worldgen_chunks_scanned': worldgen['overworld_chunks_scanned'] + worldgen['end_chunks_scanned']}
    build.write(verification / 'installation.json', report)
    shutil.copy2(verify.FIXTURE / 'logs/sise-ores-unification.json', verification / 'runtime-unification.json')
    shutil.copy2(verify.FIXTURE / 'screenshots/sise-ores-materials.png', verification / 'sise-ores-materials.png')
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
