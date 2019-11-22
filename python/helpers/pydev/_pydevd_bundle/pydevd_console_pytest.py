
from _pydev_bundle import pydev_log
import traceback


def enable_pytest_output():
    try:
        import _pytest

        if hasattr(_pytest, "debugging"):  # pytest >= 3
            from _pytest.debugging import pytestPDB as _pytestDebug
        else:
            raise ValueError("Failed to find debugger in _pytest")

        plugin_manager = _pytestDebug._pluginmanager
        if plugin_manager is not None:
            capman = plugin_manager.getplugin("capturemanager")
            if hasattr(capman, "suspend"):  # pytest 4
                capman.suspend(in_=True)
            elif hasattr(capman, "suspend_global_capture"):  # pytest 3
                capman.suspend_global_capture(in_=True)
            else:
                raise ValueError("Failed to find suspend method")

    except Exception:
        pydev_log.debug("Failed to enable pytest output: %s" % traceback.format_exc())
