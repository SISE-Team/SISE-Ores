"""Full-pack client/world verification in an isolated game directory, offline identity."""
import hashlib
import importlib.util
import json
import shutil
import subprocess
import sys
import uuid
import zipfile
import build

FIXTURE = build.ROOT / '.work/test-sise-ores-worldgen-20261011'


def prepare():
    import worldgen_inventory
    worldgen_inventory.main()
    classes = build.BUILD / 'probe-classes'
    build.compile_sources(build.HERE / 'src/probe/java', classes, str(build.BUILD / 'classes') + ';')
    probe = build.BUILD / 'libs/sise-ores-probe.jar'
    with zipfile.ZipFile(probe, 'w', zipfile.ZIP_DEFLATED) as archive:
        for p in classes.rglob('*.class'):
            archive.write(p, p.relative_to(classes).as_posix())
        archive.writestr('META-INF/neoforge.mods.toml', '''modLoader="javafml"
loaderVersion="[10,)"
license="MIT"
[[mods]]
modId="sise_ores_probe"
version="1.0"
displayName="SISE Ores isolated verification"
[[dependencies.sise_ores_probe]]
modId="sise_ores"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="BOTH"
''')
    FIXTURE.mkdir(parents=True, exist_ok=True)
    for name in ('mods', 'config', 'defaultconfigs', 'kubejs'):
        shutil.copytree(build.ROOT / name, FIXTURE / name, dirs_exist_ok=True)
    # Remove only our older fixture artifacts, after validating the fixture marker/path.
    for p in (FIXTURE / 'mods').glob('SISE-Ores-*.jar'):
        if p.resolve().is_relative_to(FIXTURE.resolve()) and p.name != f'SISE-Ores-{build.VERSION}.jar':
            p.unlink()
    for p in [build.BUILD / f'libs/SISE-Ores-{build.VERSION}.jar', probe]:
        shutil.copy2(p, FIXTURE / 'mods' / p.name)
    (FIXTURE / 'SISE_ORES_AUTOTEST_ONLY').write_text('Isolated verification only', encoding='utf-8')
    shutil.copy2(build.HERE / 'verification/worldgen-inventory.json', FIXTURE / 'worldgen-inventory.json')
    witness_file = build.HERE / 'verification/crafting-witnesses.json'
    if witness_file.exists(): shutil.copy2(witness_file, FIXTURE / witness_file.name)
    options = (build.ROOT / 'options.txt').read_text('utf-8')
    settings = {'lang': 'zh_cn', 'renderDistance': '3', 'simulationDistance': '5', 'maxFps': '60',
                'fullscreen': 'false', 'overrideWidth': '1280', 'overrideHeight': '800', 'guiScale': '2', 'onboardAccessibility': 'false'}
    lines = []
    for line in options.splitlines():
        key = line.split(':', 1)[0]
        lines.append(key + ':' + settings.pop(key) if key in settings else line)
    lines.extend(k + ':' + v for k, v in settings.items())
    (FIXTURE / 'options.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def launch():
    previous = FIXTURE / 'sise-ores-probe.json'
    if previous.exists(): previous.unlink()
    spec = importlib.util.spec_from_file_location('pcl_verify', build.ROOT / '.work/instance-maintenance-20261008/verify_pcl_launch.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    args = module.generated_arguments()
    main = args.index('net.neoforged.fml.startup.Client')
    args[0] = str(build.JAVA / 'java.exe')
    replacements = {'--username': 'SiseOresFixture', '--uuid': uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:SiseOresFixture').digest(), version=3).hex,
                    '--accessToken': '0', '--clientId': '', '--xuid': '', '--userProperties': '{}', '--gameDir': str(FIXTURE),
                    '--quickPlayPath': str(FIXTURE / 'quickPlay.json')}
    for i in range(main + 1, len(args) - 1):
        if args[i] in replacements:
            args[i + 1] = replacements[args[i]]
    for i in range(1, main):
        if args[i].startswith('-Xmx'): args[i] = '-Xmx6G'
        if args[i].startswith('-Xms'): args[i] = '-Xms1G'
        if args[i].startswith('-Djna.tmpdir='): args[i] = '-Djna.tmpdir=' + str(FIXTURE / 'native-cache')
    args[main:main] = ['-Dsise.ores.verify=true', '-Dsise.ores.fixture=' + str(FIXTURE)]
    report_path = build.HERE / 'verification'
    with (report_path / 'client-console.log').open('w', encoding='utf-8') as output:
        process = subprocess.Popen(args, cwd=FIXTURE, stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        build.write(report_path / 'active-test.json', {'pid': process.pid, 'fixture': str(FIXTURE)})
        print('Test client PID:', process.pid, flush=True)
        result = process.wait()
    report = json.loads((FIXTURE / 'sise-ores-probe.json').read_text('utf-8')) if (FIXTURE / 'sise-ores-probe.json').exists() else {}
    passed = result == 0 and report.get('passed') is True
    build.write(report_path / 'client-result.json', {'exit_code': result, 'passed': passed, 'probe': report})
    print('Exit:', result, 'Passed:', passed, 'Assertions:', report.get('assertions'), 'Error:', report.get('error'), flush=True)
    return 0 if passed else 1


if __name__ == '__main__':
    if sys.argv[1] == 'prepare': prepare()
    else: raise SystemExit(launch())
