import json
import os
import subprocess
import sys
from textwrap import dedent


def test_could_load_extensions():
    my_extensions_path = os.path.join(os.path.dirname(__file__), "my_extensions")

    code = dedent("""
        import json
        from _pydevd_bundle.pydevd_extension_utils import EXTENSION_MANAGER_INSTANCE as em
        em._load_modules()
        print(json.dumps([m.__name__ for m in em.loaded_extensions]))
    """).strip()

    expected_modules = {
        'pydevd_plugins.extensions.pydevd_plugin_test_events',
        'pydevd_plugins.extensions.pydevd_plugin_test_exttype',
        'pydevd_plugins.extensions.types.pydevd_plugin_numpy_types',
        'pydevd_plugins.extensions.types.pydevd_plugins_django_form_str',
    }

    result = subprocess.run(
        [sys.executable, "-c", code],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env={"PYTHONPATH": my_extensions_path}
    )

    loaded_modules = json.loads(result.stdout)

    assert len(loaded_modules) == len(expected_modules)
    assert result.returncode == 0
    assert result.stderr == ""
    assert set(loaded_modules) == expected_modules
