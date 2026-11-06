"""
IPython for the Debug Console of the debugpy backend (PY-87087).

One IPython shell lives in the debuggee process. The shell is bound to the paused frame for every cell,
the same way the pydevd backend does it in `_PyDevFrontEnd.update`. The execution counter, `Out[N]`,
`_` and the magics then work across the evaluations and across the pauses.

The IDE turns this on with the `setDebuggerProperty` request. The IDE detects the greeting line
of the first cell and draws the `In [N]:` prompts itself.
"""

import codeop
import sys

from _pydevd_bundle.pydevd_save_locals import update_globals_and_locals

_shell = None
_unavailable = False
_enabled = False

# The names which the shell puts into its namespace when it starts, such as `__builtin__`.
_shell_names = frozenset()

# The names which the cells defined. Only these and the names of the shell go from one cell to the next,
# so the variables of one frame do not become the variables of another frame.
_cell_names = set()

# The lines of an unfinished statement, as the pydevd frontend keeps them in `_curr_exec_lines`.
_pending_lines = []

_SENTINEL = object()

# The `context` of the evaluate request which comes from the Debug Console.
CONSOLE_CONTEXT = "pydevConsole"

# The `type` of the evaluate response which tells the IDE that the cell is not finished.
MORE_RESPONSE_TYPE = "PyDevIpythonMore"


def is_enabled():
    return _enabled


def set_enabled(enabled):
    global _enabled
    _enabled = bool(enabled)
    del _pending_lines[:]


def reset_pending():
    """Drops the unfinished statement, for example after an internal error."""
    del _pending_lines[:]


def _get_shell():
    """Returns the shell, or None when IPython is not importable in the debuggee."""
    global _shell, _unavailable, _shell_names
    if _shell is not None or _unavailable:
        return _shell
    try:
        from IPython.core.interactiveshell import InteractiveShell
        from traitlets.config import Config
        import IPython

        if InteractiveShell.initialized():
            # The process has a shell of its own, for example a Jupyter kernel. `_bind` would clear the namespace
            # of that shell, so the console keeps the plain evaluation.
            _unavailable = True
            return None

        config = Config()
        if sys.version_info < (3, 12):
            # The thread which saves the history deadlocks under sys.settrace while the frame is suspended.
            config.HistoryManager.enabled = False
            # The detailed traceback takes over a minute on these versions, the plain one is instant.
            config.InteractiveShell.xmode = "Plain"
        shell = InteractiveShell.instance(config=config, colors="NoColor")
    except Exception:
        _unavailable = True
        return None

    _shell = shell
    _shell_names = frozenset(shell.user_ns)
    # The IDE detects this line and switches the console to the IPython mode.
    sys.stdout.write("PyDev console: using IPython %s\n" % IPython.__version__)
    return _shell


def _is_shell_name(shell, key, value):
    """Tells whether the shell made `key`, for example `Out`, `_` or `_i3`, so it must not reach the frame."""
    # IPython keeps the values it adds after the start in `user_ns_hidden`. `%who` reads it the same way.
    return key in _shell_names or shell.user_ns_hidden.get(key, _SENTINEL) is value


def _bind(shell, namespace):
    """Makes `namespace` the namespace of `shell`, and keeps what the earlier cells defined."""
    for key, value in shell.user_ns.items():
        if key in _cell_names or _is_shell_name(shell, key, value):
            namespace.setdefault(key, value)
    shell.user_global_ns.clear()
    shell.user_global_ns.update(namespace)
    shell.user_ns = shell.user_global_ns


def _is_complete(cell):
    """The rule of `_PyDevFrontEnd.is_complete`, so both backends ask for more lines in the same cases."""
    if cell in ("", "\n"):
        return True
    try:
        clean = cell.rstrip("\n")
        if not clean.endswith("\\"):
            clean += "\n\n"
        return codeop.compile_command(clean, "<string>", "exec") is not None
    except Exception:
        # IPython reports the error when it runs the cell.
        return True


def run_cell(frame, code):
    """
    Adds the line `code` of the console to the cell and runs the cell when it is finished.

    Returns None when IPython is not available, so that the caller can use the plain evaluation instead.
    Returns True when the cell needs more lines. Returns False when the cell ran. The output of the
    cell goes to sys.stdout and sys.stderr.
    """
    shell = _get_shell()
    if shell is None:
        return None

    _pending_lines.append(code.replace("@LINE@", "\n"))
    cell = "\n".join(_pending_lines)
    if not _is_complete(cell):
        return True
    del _pending_lines[:]

    namespace = {}
    namespace.update(frame.f_globals)
    namespace.update(frame.f_locals)
    initial = namespace.copy()
    _bind(shell, namespace)

    # Python 3.11 and older run IPython up to five times slower while the thread is traced.
    trace = None
    untraced = sys.version_info < (3, 12)
    if untraced:
        trace = sys.gettrace()
        sys.settrace(None)
    try:
        shell.run_cell(cell, store_history=True)
    finally:
        if untraced:
            sys.settrace(trace)
        updated = {}
        for key, value in shell.user_ns.items():
            if key in initial:
                updated[key] = value
            elif not _is_shell_name(shell, key, value):
                updated[key] = value
                _cell_names.add(key)
        update_globals_and_locals(updated, initial, frame)
    return False
