import subprocess
import sys


def test_virtualenv_py3_pyz_integrity(helpers_root):
    result = subprocess.run(
        [sys.executable, helpers_root / "virtualenv-py3.pyz", "--version"],
        capture_output=True, text=True
    )
    assert result.returncode == 0
    assert "virtualenv" in result.stdout
