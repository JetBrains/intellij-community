from _pydevd_bundle.pydevd_constants import dict_keys

try:
    from code import InteractiveConsole
except ImportError:
    from _pydevd_bundle.pydevconsole_code_for_ironpython import InteractiveConsole

import os
import sys
import traceback
from code import InteractiveInterpreter, InteractiveConsole
from code import compile_command

from _pydev_bundle.pydev_code_executor import BaseCodeExecutor
from _pydev_bundle.pydev_console_types import CodeFragment, Command
from _pydev_bundle.pydev_imports import Exec
from _pydevd_bundle import pydevd_vars, pydevd_save_locals
from _pydevd_bundle.pydevd_console_pytest import enable_pytest_output

try:
    import __builtin__
except:
    import builtins as __builtin__  # @UnresolvedImport


class CodeExecutor(BaseCodeExecutor):
    def __init__(self):
        super(CodeExecutor, self).__init__()

        self.namespace = {}
        self.interpreter = InteractiveConsole(self.namespace)

    def do_add_exec(self, codeFragment):
        command = Command(self.interpreter, codeFragment)
        command.run()
        return command.more

    def get_namespace(self):
        return self.namespace


if 'IPYTHONENABLE' in os.environ:
    IPYTHON = os.environ['IPYTHONENABLE'] == 'True'
else:
    IPYTHON = True

PYTEST_RUN_CONFIG = 'PYTEST_RUN_CONFIG' in os.environ

try:
    try:
        exitfunc = sys.exitfunc
    except AttributeError:
        exitfunc = None

    if IPYTHON:
        from _pydev_bundle.pydev_ipython_code_executor import CodeExecutor
        if exitfunc is not None:
            sys.exitfunc = exitfunc
        else:
            try:
                delattr(sys, 'exitfunc')
            except:
                pass
except:
    IPYTHON = False
    pass


def get_ipython_hidden_vars():
    if IPYTHON and hasattr(__builtin__, 'interpreter'):
        code_executor = get_code_executor()
        return code_executor.get_ipython_hidden_vars_dict()
    else:
        try:
            ipython_shell = get_ipython()
            from _pydev_bundle.pydev_ipython_console_011 import get_ipython_hidden_vars
            return get_ipython_hidden_vars(ipython_shell)
        except:
            pass


def get_code_executor():
    try:
        code_executor = getattr(__builtin__, 'interpreter')
    except AttributeError:
        code_executor = CodeExecutor()
        __builtin__.interpreter = code_executor
        print(code_executor.get_greeting_msg())

    return code_executor


# ===============================================================================
# Debugger integration
# ===============================================================================

def exec_code(code, globals, locals, debugger):
    code_executor = get_code_executor()
    code_executor.interpreter.update(globals, locals)

    res = code_executor.need_more(code)

    if res:
        return True

    code_executor.add_exec(code, debugger)

    ipython = code_executor.interpreter.ipython
    for key in dict_keys(ipython.user_ns):
        locals[key] = ipython.user_ns[key]
    for key in dict_keys(locals):
        if key not in ipython.user_ns:
            locals.pop(key)

    return False


class ConsoleWriter(InteractiveInterpreter):
    skip = 0

    def __init__(self, locals=None):
        InteractiveInterpreter.__init__(self, locals)

    def write(self, data):
        # if (data.find("global_vars") == -1 and data.find("pydevd") == -1):
        if self.skip > 0:
            self.skip -= 1
        else:
            if data == "Traceback (most recent call last):\n":
                self.skip = 1
            sys.stderr.write(data)

    def showsyntaxerror(self, filename=None):
        """Display the syntax error that just occurred."""
        # Override for avoid using sys.excepthook PY-12600
        type, value, tb = sys.exc_info()
        sys.last_type = type
        sys.last_value = value
        sys.last_traceback = tb
        if filename and type is SyntaxError:
            # Work hard to stuff the correct filename in the exception
            try:
                msg, (dummy_filename, lineno, offset, line) = value.args
            except ValueError:
                # Not the format we expect; leave it alone
                pass
            else:
                # Stuff in the right filename
                value = SyntaxError(msg, (filename, lineno, offset, line))
                sys.last_value = value
        list = traceback.format_exception_only(type, value)
        sys.stderr.write(''.join(list))

    def showtraceback(self):
        """Display the exception that just occurred."""
        # Override for avoid using sys.excepthook PY-12600
        try:
            type, value, tb = sys.exc_info()
            sys.last_type = type
            sys.last_value = value
            sys.last_traceback = tb
            tblist = traceback.extract_tb(tb)
            del tblist[:1]
            lines = traceback.format_list(tblist)
            if lines:
                lines.insert(0, "Traceback (most recent call last):\n")
            lines.extend(traceback.format_exception_only(type, value))
        finally:
            tblist = tb = None
        sys.stderr.write(''.join(lines))


def console_exec(thread_id, frame_id, expression, dbg):
    """returns 'False' in case expression is partially correct
    """
    frame = pydevd_vars.find_frame(thread_id, frame_id)

    is_multiline = expression.count('@LINE@') > 1
    try:
        expression = str(expression.replace('@LINE@', '\n'))
    except UnicodeEncodeError as e:
        expression = expression.replace('@LINE@', '\n')

    # Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    # (Names not resolved in generator expression in method)
    # See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = {}
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals)  # locals later because it has precedence over the actual globals

    if PYTEST_RUN_CONFIG:
        enable_pytest_output()

    if IPYTHON:
        need_more = exec_code(CodeFragment(expression), updated_globals, updated_globals, dbg)
        if not need_more:
            update_frame_local_variables_and_save(frame, updated_globals)
        return need_more

    interpreter = ConsoleWriter()

    if not is_multiline:
        try:
            code = compile_command(expression)
        except (OverflowError, SyntaxError, ValueError):
            # Case 1
            interpreter.showsyntaxerror()
            return False
        if code is None:
            # Case 2
            return True
    else:
        code = expression

    # Case 3

    try:
        # It is important that globals and locals we pass to the exec function are the same object.
        # Otherwise generator expressions can confuse their scope. Passing updated_globals dictionary seems to be a safe option here
        # because it contains globals and locals in the right precedence.
        # See: https://stackoverflow.com/questions/15866398/why-is-a-python-generator-confusing-its-scope-with-global-in-an-execd-script.
        Exec(code, updated_globals, updated_globals)
    except SystemExit:
        raise
    except:
        interpreter.showtraceback()
    else:
        update_frame_local_variables_and_save(frame, updated_globals)
    return False


def update_frame_local_variables_and_save(frame, var_dict):
    """Update the frame local variables with the values from `var_dict`, remove those that no longer exist, and save."""
    for var_name in var_dict:
        if var_name not in frame.f_globals:
            frame.f_locals[var_name] = var_dict[var_name]
        elif var_dict[var_name] is not frame.f_globals[var_name]:
            frame.f_locals[var_name] = var_dict[var_name]
    for var_name in frame.f_locals:
        if var_name not in var_dict:
            frame.f_locals.pop(var_name)
    pydevd_save_locals.save_locals(frame)
