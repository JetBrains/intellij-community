import os
import subprocess
import sys
import textwrap

import pytest

HELPERS_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEBUGPY_PYDEVD_DIR = os.path.join(HELPERS_DIR, "debugpy", "_vendored", "pydevd")

# Stubs a Qt binding: QtGui has no QApplication, as in Qt 5 and Qt 6.
ENABLE_QT_SCRIPT = textwrap.dedent("""
    import importlib.machinery
    import importlib.util
    import sys
    import types

    sys.path.insert(0, sys.argv[1])
    binding = sys.argv[2]

    class QCoreApplication(object):
        _instance = None

        @classmethod
        def instance(cls):
            return cls._instance

    class QApplication(QCoreApplication):
        def __init__(self, argv):
            QCoreApplication._instance = self

    package = types.ModuleType(binding)
    package.__path__ = []
    package.__version__ = "6.11.2"
    sys.modules[binding] = package
    for name in ("QtCore", "QtGui", "QtSvg", "QtWidgets"):
        module = types.ModuleType(binding + "." + name)
        module.__spec__ = importlib.machinery.ModuleSpec(module.__name__, None)
        setattr(package, name, module)
        sys.modules[module.__name__] = module
    package.QtCore.QCoreApplication = QCoreApplication
    package.QtWidgets.QApplication = QApplication

    from pydev_ipython.inputhook import enable_gui, get_inputhook, set_return_control_callback

    set_return_control_callback(lambda: True)
    enable_gui("qt")
    app = QCoreApplication.instance()
    print(type(app).__name__, get_inputhook() is not None, sys.modules.get(binding) is package)
""")


@pytest.mark.skipif(sys.version_info < (3, 8), reason="debugpy needs Python 3.8 or later")
@pytest.mark.parametrize("binding", ["PySide6", "PySide2"])
def test_debugpy_enables_qt_event_loop_for_pyside(binding):
    result = subprocess.run(
        [sys.executable, "-I", "-c", ENABLE_QT_SCRIPT, DEBUGPY_PYDEVD_DIR, binding],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "QApplication True True"
