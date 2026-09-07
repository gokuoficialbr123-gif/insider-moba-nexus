from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
source = root / 'web' / 'index.html'
targets = [
    root / 'android' / 'app' / 'src' / 'main' / 'assets' / 'index.html',
    root / 'ios' / 'NEXUS' / 'NEXUS' / 'index.html',
]
if not source.exists():
    raise SystemExit(f'Arquivo principal ausente: {source}')
for target in targets:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    print(f'sync: {source.relative_to(root)} -> {target.relative_to(root)}')
