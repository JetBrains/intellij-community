import sys
from importlib import reload

import pytest


@pytest.fixture
def mock_env_use_cython_yes(monkeypatch):
    monkeypatch.setenv("PYDEVD_USE_CYTHON", "YES")


@pytest.fixture
def mock_env_use_cython_no(monkeypatch):
    monkeypatch.setenv("PYDEVD_USE_CYTHON", "NO")


@pytest.fixture
def mock_env_use_cython_none(monkeypatch):
    monkeypatch.delenv("PYDEVD_USE_CYTHON", raising=False)


@pytest.fixture
def mock_env_use_cython_unexpected(monkeypatch):
    monkeypatch.setenv("PYDEVD_USE_CYTHON", "HELLO")


def load_wrapper():
    from _pydevd_bundle import pydevd_pep_669_tracing_wrapper as wrapper

    reload(wrapper)
    return wrapper


def assert_wrapper_mod(mod):
    wrapper = load_wrapper()

    assert wrapper.enable_pep669_monitoring is mod.enable_pep669_monitoring
    assert wrapper.global_cache_skips is mod.global_cache_skips
    assert wrapper.global_cache_frame_skips is mod.global_cache_frame_skips


def assert_dummy_restart_events():
    wrapper = load_wrapper()

    assert wrapper.restart_events is wrapper._dummy_restart_events


def assert_monitoring_restart_events():
    wrapper = load_wrapper()

    assert sys.version_info >= (3, 12)  # only available for Python >=3.12
    assert wrapper.restart_events is sys.monitoring.restart_events


@pytest.mark.ge_python312
def test_ge_python312_cython_is_explicitly_disabled(mock_env_use_cython_no):
    from _pydevd_bundle import pydevd_pep_669_tracing as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_monitoring_restart_events()


@pytest.mark.ge_python312
def test_ge_python312_cython_is_explicitly_enabled(mock_env_use_cython_yes):
    from _pydevd_bundle import pydevd_pep_669_tracing_cython as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_monitoring_restart_events()


@pytest.mark.ge_python312
def test_ge_python312_cython_env_is_none(mock_env_use_cython_none):
    from _pydevd_bundle import pydevd_pep_669_tracing_cython as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_monitoring_restart_events()


@pytest.mark.ge_python312
def test_ge_python312_cython_env_is_unexpected(mock_env_use_cython_unexpected):
    from _pydevd_bundle import pydevd_pep_669_tracing_cython as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_monitoring_restart_events()


@pytest.mark.le_python311
def test_le_python311_cython_is_explicitly_disabled(mock_env_use_cython_no):
    from _pydevd_bundle import pydevd_pep_669_tracing as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_dummy_restart_events()


@pytest.mark.le_python311
def test_le_python311_cython_is_explicitly_enabled(mock_env_use_cython_yes):
    from _pydevd_bundle import pydevd_pep_669_tracing as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_dummy_restart_events()


@pytest.mark.le_python311
def test_le_python311_cython_env_is_none(mock_env_use_cython_none):
    from _pydevd_bundle import pydevd_pep_669_tracing as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_dummy_restart_events()


@pytest.mark.le_python311
def test_le_python311_cython_env_is_unexpected(mock_env_use_cython_unexpected):
    from _pydevd_bundle import pydevd_pep_669_tracing as expected_mod

    assert_wrapper_mod(expected_mod)
    assert_dummy_restart_events()
