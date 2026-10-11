from pathlib import Path
import json
import subprocess
import zipfile
import build

build.BUILD.mkdir(parents=True, exist_ok=True)
corpus = {}
for jar in sorted((build.ROOT / 'mods').glob('*.jar')):
    if jar.name.startswith('SISE-Ores-'):
        continue
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.startswith('data/') and '/recipe/' in name and name.endswith('.json'):
                try:
                    corpus[name] = json.loads(archive.read(name))
                except (ValueError, UnicodeError):
                    pass
path = build.BUILD / 'recipe-corpus.json'
build.write(path, corpus)
classes = build.BUILD / 'test-classes'
build.compile_sources(build.HERE / 'src/test/java', classes, str(build.BUILD / 'classes') + ';')
classpath = ';'.join(map(str, [classes, build.BUILD / 'classes', build.RES])) + ';' + build.classpath()
subprocess.run([str(build.JAVA / 'java.exe'), '-cp', classpath, 'local.sise.ores.TransformTests',
                str(path), str(build.HERE / 'verification/transform-tests.json')], check=True)
