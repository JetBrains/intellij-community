import pytest

from _pydevd_bundle.pydevd_constants import IS_PY3K

if IS_PY3K:
    import sys
    import subprocess
    from pathlib import Path


@pytest.mark.python3(reason="`-I` is introduced in 3.4")
def test_isolated_mode():
    script = Path("pydevd.py")
    result = subprocess.run(
        [sys.executable, "-I", str(script), "--help"],
        cwd=Path.cwd(),
    )

    assert result.returncode == 0
