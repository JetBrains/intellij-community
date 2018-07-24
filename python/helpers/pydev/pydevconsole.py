'''
Entry point module to start the interactive console.
'''
from _pydev_bundle._pydev_getopt import gnu_getopt
from _pydev_imps._pydev_saved_modules import thread
from _pydev_comm.rpc import make_rpc_client, start_rpc_server, start_rpc_server_and_make_client

start_new_thread = thread.start_new_thread

try:
    from code import InteractiveConsole
except ImportError:
    from _pydevd_bundle.pydevconsole_code_for_ironpython import InteractiveConsole

from code import compile_command
from code import InteractiveInterpreter

import os
import sys

from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_constants import INTERACTIVE_MODE_AVAILABLE, dict_keys

import traceback
from _pydev_bundle import fix_getpass
fix_getpass.fix_getpass()

from _pydevd_bundle import pydevd_vars, pydevd_save_locals

from _pydev_bundle.pydev_imports import Exec, _queue

try:
    import __builtin__
except:
    import builtins as __builtin__  # @UnresolvedImport

from _pydev_bundle.pydev_console_utils import BaseInterpreterInterface, BaseStdIn
from _pydev_bundle.pydev_console_utils import CodeFragment

IS_PYTHON_3_ONWARDS = sys.version_info[0] >= 3
IS_PY24 = sys.version_info[0] == 2 and sys.version_info[1] == 4

class Command:
    def __init__(self, interpreter, code_fragment):
        """
        :type code_fragment: CodeFragment
        :type interpreter: InteractiveConsole
        """
        self.interpreter = interpreter
        self.code_fragment = code_fragment
        self.more = None


    def symbol_for_fragment(code_fragment):
        if code_fragment.is_single_line:
            symbol = 'single'
        else:
            symbol = 'exec' # Jython doesn't support this
        return symbol
    symbol_for_fragment = staticmethod(symbol_for_fragment)

    def run(self):
        text = self.code_fragment.text
        symbol = self.symbol_for_fragment(self.code_fragment)

        self.more = self.interpreter.runsource(text, '<input>', symbol)

try:
    try:
        execfile #Not in Py3k
    except NameError:
        from _pydev_bundle.pydev_imports import execfile

        __builtin__.execfile = execfile
except:
    pass

# Pull in runfile, the interface to UMD that wraps execfile
from _pydev_bundle.pydev_umd import runfile, _set_globals_function
if sys.version_info[0] >= 3:
    import builtins  # @UnresolvedImport
    builtins.runfile = runfile
else:
    import __builtin__
    __builtin__.runfile = runfile

#=======================================================================================================================
# InterpreterInterface
#=======================================================================================================================
class InterpreterInterface(BaseInterpreterInterface):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, mainThread, connect_status_queue=None, rpc_client=None):
        BaseInterpreterInterface.__init__(self, mainThread, connect_status_queue, rpc_client)
        self.namespace = {}
        self.interpreter = InteractiveConsole(self.namespace)
        self._input_error_printed = False

    def do_add_exec(self, codeFragment):
        command = Command(self.interpreter, codeFragment)
        command.run()
        return command.more

    def get_namespace(self):
        return self.namespace

    def close(self):
        sys.exit(0)


class _ProcessExecQueueHelper:
    _debug_hook = None
    _return_control_osc = False

def set_debug_hook(debug_hook):
    _ProcessExecQueueHelper._debug_hook = debug_hook


def activate_mpl_if_already_imported(interpreter):
    if interpreter.mpl_modules_for_patching:
        for module in dict_keys(interpreter.mpl_modules_for_patching):
            if module in sys.modules:
                activate_function = interpreter.mpl_modules_for_patching.pop(module)
                activate_function()


def init_set_return_control_back(interpreter):
    from pydev_ipython.inputhook import set_return_control_callback

    def return_control():
        ''' A function that the inputhooks can call (via inputhook.stdin_ready()) to find
            out if they should cede control and return '''
        if _ProcessExecQueueHelper._debug_hook:
            # Some of the input hooks check return control without doing
            # a single operation, so we don't return True on every
            # call when the debug hook is in place to allow the GUI to run
            # XXX: Eventually the inputhook code will have diverged enough
            # from the IPython source that it will be worthwhile rewriting
            # it rather than pretending to maintain the old API
            _ProcessExecQueueHelper._return_control_osc = not _ProcessExecQueueHelper._return_control_osc
            if _ProcessExecQueueHelper._return_control_osc:
                return True

        if not interpreter.exec_queue.empty():
            return True
        return False

    set_return_control_callback(return_control)


def init_mpl_in_console(interpreter):
    init_set_return_control_back(interpreter)

    if not INTERACTIVE_MODE_AVAILABLE:
        return

    activate_mpl_if_already_imported(interpreter)
    from _pydev_bundle.pydev_import_hook import import_hook_manager
    for mod in dict_keys(interpreter.mpl_modules_for_patching):
        import_hook_manager.add_module_name(mod, interpreter.mpl_modules_for_patching.pop(mod))


def process_exec_queue(interpreter):
    init_mpl_in_console(interpreter)
    from pydev_ipython.inputhook import get_inputhook

    while 1:
        # Running the request may have changed the inputhook in use
        inputhook = get_inputhook()

        if _ProcessExecQueueHelper._debug_hook:
            _ProcessExecQueueHelper._debug_hook()

        if inputhook:
            try:
                # Note: it'll block here until return_control returns True.
                inputhook()
            except:
                import traceback;traceback.print_exc()
        try:
            try:
                code_fragment = interpreter.exec_queue.get(block=True, timeout=1/20.) # 20 calls/second
            except _queue.Empty:
                continue

            if hasattr(code_fragment, '__call__'):
                # It can be a callable (i.e.: something that must run in the main
                # thread can be put in the queue for later execution).
                code_fragment()
            else:
                more = interpreter.add_exec(code_fragment)
        except KeyboardInterrupt:
            interpreter.buffer = None
            continue
        except SystemExit:
            raise
        except:
            type, value, tb = sys.exc_info()
            traceback.print_exception(type, value, tb, file=sys.__stderr__)
            exit()


if 'IPYTHONENABLE' in os.environ:
    IPYTHON = os.environ['IPYTHONENABLE'] == 'True'
else:
    IPYTHON = True

try:
    try:
        exitfunc = sys.exitfunc
    except AttributeError:
        exitfunc = None

    if IPYTHON:
        from _pydev_bundle.pydev_ipython_console import InterpreterInterface
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


#=======================================================================================================================
# _DoExit
#=======================================================================================================================
def do_exit(*args):
    '''
        We have to override the exit because calling sys.exit will only actually exit the main thread,
        and as we're in a Xml-rpc server, that won't work.
    '''

    try:
        import java.lang.System

        java.lang.System.exit(1)
    except ImportError:
        if len(args) == 1:
            os._exit(args[0])
        else:
            os._exit(0)


def enable_thrift_logging():
    """Sets up `thriftpy` logger

    The logger is used in `thriftpy/server.py` for logging exceptions.
    """
    import logging

    # create logger
    logger = logging.getLogger('_jetbrains_thriftpy')
    logger.setLevel(logging.DEBUG)

    # create console handler and set level to debug
    ch = logging.StreamHandler()
    ch.setLevel(logging.DEBUG)

    # create formatter
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')

    # add formatter to ch
    ch.setFormatter(formatter)

    # add ch to logger
    logger.addHandler(ch)


def start_server(port):
    if port is None:
        port = 0

    # 0. General stuff

    #replace exit (see comments on method)
    #note that this does not work in jython!!! (sys method can't be replaced).
    sys.exit = do_exit

    from pydev_console.thrift_communication import console_thrift

    enable_thrift_logging()

    server_service = console_thrift.PythonConsoleBackendService
    client_service = console_thrift.PythonConsoleFrontendService

    # 1. Start Python console server


    interpreter = InterpreterInterface(threading.currentThread(), None, rpc_client=client_service)

    # `InterpreterInterface` implements all methods required for the handler
    server_handler = interpreter

    server_socket = start_rpc_server_and_make_client('', port, server_service, client_service, server_handler)


    # 2. Print server port for the IDE

    _, server_port = server_socket.getsockname()
    print(server_port)

    # 3. Wait for IDE to connect to the server

    process_exec_queue(interpreter)


def start_client(host, port):
    #replace exit (see comments on method)
    #note that this does not work in jython!!! (sys method can't be replaced).
    sys.exit = do_exit

    from pydev_console.thrift_communication import console_thrift

    enable_thrift_logging()

    client_service = console_thrift.PythonConsoleFrontendService

    client, server_transport = make_rpc_client(client_service, host, port)

    interpreter = InterpreterInterface(threading.currentThread(), rpc_client=client)

    # we do not need to start the server in a new thread because it does not need to accept a client connection, it already has it


    # todo do we need the following:
    # # Tell UMD the proper default namespace
    # _set_globals_function(interpreter.get_namespace)

    server_service = console_thrift.PythonConsoleBackendService

    # `InterpreterInterface` implements all methods required for the handler
    server_handler = interpreter

    start_rpc_server(server_transport, server_service, server_handler)

    process_exec_queue(interpreter)


def get_ipython_hidden_vars():
    if IPYTHON and hasattr(__builtin__, 'interpreter'):
        interpreter = get_interpreter()
        return interpreter.get_ipython_hidden_vars_dict()


def get_interpreter():
    try:
        interpreterInterface = getattr(__builtin__, 'interpreter')
    except AttributeError:
        interpreterInterface = InterpreterInterface(None, None, threading.currentThread())
        __builtin__.interpreter = interpreterInterface
        print(interpreterInterface.get_greeting_msg())

    return interpreterInterface


def get_completions(text, token, globals, locals):
    interpreterInterface = get_interpreter()

    interpreterInterface.interpreter.update(globals, locals)

    return interpreterInterface.getCompletions(text, token)

#===============================================================================
# Debugger integration
#===============================================================================

def exec_code(code, globals, locals, debugger):
    interpreterInterface = get_interpreter()
    interpreterInterface.interpreter.update(globals, locals)

    res = interpreterInterface.need_more(code)

    if res:
        return True

    interpreterInterface.add_exec(code, debugger)

    return False



class ConsoleWriter(InteractiveInterpreter):
    skip = 0

    def __init__(self, locals=None):
        InteractiveInterpreter.__init__(self, locals)

    def write(self, data):
        #if (data.find("global_vars") == -1 and data.find("pydevd") == -1):
        if self.skip > 0:
            self.skip -= 1
        else:
            if data == "Traceback (most recent call last):\n":
                self.skip = 1
            sys.stderr.write(data)

    def showsyntaxerror(self, filename=None):
        """Display the syntax error that just occurred."""
        #Override for avoid using sys.excepthook PY-12600
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
        #Override for avoid using sys.excepthook PY-12600
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
    expression = str(expression.replace('@LINE@', '\n'))

    #Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    #(Names not resolved in generator expression in method)
    #See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = {}
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals) #locals later because it has precedence over the actual globals

    if IPYTHON:
        need_more =  exec_code(CodeFragment(expression), updated_globals, frame.f_locals, dbg)
        if not need_more:
            pydevd_save_locals.save_locals(frame)
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

    #Case 3

    try:
        Exec(code, updated_globals, frame.f_locals)

    except SystemExit:
        raise
    except:
        interpreter.showtraceback()
    else:
        pydevd_save_locals.save_locals(frame)
    return False

#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    #Important: don't use this module directly as the __main__ module, rather, import itself as pydevconsole
    #so that we don't get multiple pydevconsole modules if it's executed directly (otherwise we'd have multiple
    #representations of its classes).
    #See: https://sw-brainwy.rhcloud.com/tracker/PyDev/446:
    #'Variables' and 'Expressions' views stopped working when debugging interactive console
    import pydevconsole
    sys.stdin = pydevconsole.BaseStdIn(sys.stdin)

    # parse command-line arguments
    optlist, _ = gnu_getopt(sys.argv, 'm:h:p', ['mode=', 'host=', 'port='])
    mode = None
    host = None
    port = None
    for opt, arg in optlist:
        if opt in ('-m', '--mode'):
            mode = arg
        elif opt in ('-h', '--host'):
            host = arg
        elif opt in ('-p', '--port'):
            port = int(arg)

    if mode not in ('client', 'server'):
        sys.exit(-1)

    if mode == 'client':
        if not port:
            # port must be set for client
            sys.exit(-1)

        if not host:
            from _pydev_bundle import pydev_localhost
            host = client_host = pydev_localhost.get_localhost()

        pydevconsole.start_client(host, port)
    elif mode == 'server':
        pydevconsole.start_server(port)
