A brief description of Python Console backend in PyCharm:
=========================================================

There are 4 main types of consoles in PyCharm:

1. Python Console (a separate tool window)

  a. IPython implementation
  b. General Python implementation

2. Debug Console (console attached to the current debug session)

  a. IPython implementation
  b. General Python implementation

IPython implementation is available only if ``ipython`` installed on interpreter and an option "Use IPython if available" enabled.
Each of these consoles has its own implementation inside ``pydevd`` module.

1. Python Console (a separate tool window)

Entry point is ``pydevd/pydevconsole.py``.
After console launching, backend creates an instance of ``InterpreterInterface``.

1a. If IPython is available, ``IPythonInterpreterInterface`` is imported from ``_pydev_bundle.pydev_ipython_console``. Instance of ``IPythonInterpreterInterface`` has an attribute ``self.interpreter`` (instance of ``_PyDevIPythonFrontEnd``) - an instance of actual interpreter, which performs actual operations like code execution, variables update or runtime completion variants calculation.

1b. If IPython isn't available, console uses ``InterpreterInterface`` declared in ``pydevconsole.py``. It stores an instance of  ``InteractiveConsole`` from built-in module ``code`` (or ``IronPythonInteractiveConsole`` for ``IronPython``) in ``self.interpreter`` attribute and uses it code execution.

2. Debug Console

Debug Console always exists in the context of a debug process. IDE side sends command CMD_CONSOLE_EXEC to debugger backend, which then passes expression to ``pydevd_console_integration.console_exec()``. Before the first execution request, PyCharm initializes instance of interpreter and stores it as a builtin attribute (see ``pydevd_console_integration.get_code_executor()``). Each command executed in Debug Console is passed into ``pydevd_console_integration.console_exec()`` function.

2a. If IPython is available, ``CodeExecutor`` is imported from ``_pydev_bundle.pydev_ipython_code_executor``. Similar to ``IPythonInterpreterInterface``, ``IPythonCodeExecutor`` has a ``self.interpreter`` attribute, which contains an instance of ``_PyDevIPythonFrontEnd``. Command executions and variables update are delegated to ``self.interpreter``.

2b. If IPython isn't available, console uses ``CodeExecutor`` declared in ``_pydevd_bundle.pydevd_console_integration``. Similar to ``InterpreterInterface``, ``CodeExecutor`` has a ``self.interpreter`` attribute, which stores an instance of ``InteractiveConsole`` from built-in module ``code``. Important difference from 1b is that it **doesn't** delegate code execution to ``InteractiveConsole``, but uses built-in ``exec()`` function and updates variables manually.

Debug Console uses ``CMD_GET_COMPLETIONS`` command for getting runtime completion.
Important note: code from ``pydevd_console.py`` is used only by PyDev, but not by PyCharm.


