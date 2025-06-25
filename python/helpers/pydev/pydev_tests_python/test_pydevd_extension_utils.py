import os

import pytest


@pytest.fixture
def with_my_extensions(monkeypatch):
    my_extensions_path = os.path.join(os.path.dirname(__file__), "my_extensions")
    monkeypatch.syspath_prepend(my_extensions_path)


def test_could_load_extensions(with_my_extensions):
    from _pydevd_bundle.pydevd_extension_utils import ExtensionManager

    em = ExtensionManager()
    em._load_modules()

    expected_modules = [
        'pydevd_plugins.extensions.pydevd_plugin_test_events',
        'pydevd_plugins.extensions.pydevd_plugin_test_exttype',
        'pydevd_plugins.extensions.types.pydevd_plugin_numpy_types',
        'pydevd_plugins.extensions.types.pydevd_plugins_django_form_str',
    ]
    assert [m.__name__ for m in em.loaded_extensions] == expected_modules
