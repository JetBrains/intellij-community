try:
    from code import InteractiveConsole
except ImportError:
    from pydevconsole_code_for_ironpython import InteractiveConsole

from code import compile_command
from code import InteractiveInterpreter

import os
import sys

from pydevd_constants import USE_LIB_COPY
from pydevd_constants import IS_JYTHON

if USE_LIB_COPY:
    import _pydev_threading as threading
else:
    import threading

import traceback
import fix_getpass
fix_getpass.fixGetpass()

import pydevd_vars

from pydev_imports import Exec

try:
    if USE_LIB_COPY:
        import _pydev_Queue as _queue
    else:
        import Queue as _queue
except:
    import queue as _queue

try:
    import __builtin__
except:
    import builtins as __builtin__

try:
    False
    True
except NameError: # version < 2.3 -- didn't have the True/False builtins
    import __builtin__

    setattr(__builtin__, 'True', 1) #Python 3.0 does not accept __builtin__.True = 1 in its syntax
    setattr(__builtin__, 'False', 0)

from pydev_console_utils import BaseInterpreterInterface
from pydev_console_utils import CodeFragment

IS_PYTHON_3K = False

try:
    if sys.version_info[0] == 3:
        IS_PYTHON_3K = True
except:
    #That's OK, not all versions of python have sys.version_info
    pass

try:
    try:
        if USE_LIB_COPY:
            import _pydev_xmlrpclib as xmlrpclib
        else:
            import xmlrpclib
    except ImportError:
        import xmlrpc.client as xmlrpclib
except ImportError:
    import _pydev_xmlrpclib as xmlrpclib


class Command:
    def __init__(self, interpreter, code_fragment):
        """
        :type code_fragment: CodeFragment
        :type interpreter: InteractiveConsole
        """
        self.interpreter = interpreter
        self.code_fragment = code_fragment
        self.more = None

    @staticmethod
    def symbol_for_fragment(code_fragment):
        if code_fragment.is_single_line:
            symbol = 'single'
        else:
            symbol = 'exec' # Jython doesn't support this
        return symbol

    def run(self):
        text = self.code_fragment.text
        symbol = self.symbol_for_fragment(self.code_fragment)

        self.more = self.interpreter.runsource(text, '<input>', symbol)

try:
    try:
        execfile #Not in Py3k
    except NameError:
        from pydev_imports import execfile

        __builtin__.execfile = execfile

except:
    pass


#=======================================================================================================================
# InterpreterInterface
#=======================================================================================================================
class InterpreterInterface(BaseInterpreterInterface):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, host, client_port, mainThread):
        BaseInterpreterInterface.__init__(self, mainThread)
        self.client_port = client_port
        self.host = host
        self.namespace = {}
        self.interpreter = InteractiveConsole(self.namespace)
        self._input_error_printed = False


    def doAddExec(self, codeFragment):
        command = Command(self.interpreter, codeFragment)
        command.run()
        return command.more


    def getNamespace(self):
        return self.namespace


    def getCompletions(self, text, act_tok):
        try:
            from _pydev_completer import Completer

            completer = Completer(self.namespace, None)
            return completer.complete(act_tok)
        except:
            import traceback

            traceback.print_exc()
            return []
        
    def close(self):
        sys.exit(0)

    def get_greeting_msg(self):
        return 'PyDev console: starting.'


def process_exec_queue(interpreter):
    while 1:
        try:
            try:
                codeFragment = interpreter.exec_queue.get(block=True, timeout=0.05)
            except _queue.Empty:
                continue

            more = interpreter.addExec(codeFragment)
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
        from pydev_ipython_console import InterpreterInterface
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
def _DoExit(*args):
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


def handshake():
    return "PyCharm"


def ipython_editor(interpreter):
    def editor(file, line):
        if file is None:
            file = ""
        if line is None:
            line = "-1"
        interpreter.ipython_editor(file, line)

    return editor

#=======================================================================================================================
# StartServer
#=======================================================================================================================
def start_server(host, port, interpreter):
    if port == 0:
        host = ''

    try:
        from _pydev_xmlrpc_hook import InputHookedXMLRPCServer as XMLRPCServer  #@UnusedImport
    except:
        #I.e.: supporting the internal Jython version in PyDev to create a Jython interactive console inside Eclipse.
        from pydev_imports import SimpleXMLRPCServer as XMLRPCServer  #@Reimport

    try:
        server = XMLRPCServer((host, port), logRequests=False, allow_none=True)

    except:
        sys.stderr.write('Error starting server with host: %s, port: %s, client_port: %s\n' % (host, port, client_port))
        raise

    server.register_function(interpreter.execLine)
    server.register_function(interpreter.execMultipleLines)
    server.register_function(interpreter.getCompletions)
    server.register_function(interpreter.getFrame)
    server.register_function(interpreter.getVariable)
    server.register_function(interpreter.changeVariable)
    server.register_function(interpreter.getDescription)
    server.register_function(interpreter.close)
    server.register_function(interpreter.interrupt)
    server.register_function(handshake)
    server.register_function(interpreter.connectToDebugger)

    if IPYTHON:
        try:
            interpreter.interpreter.ipython.hooks.editor = ipython_editor(interpreter)
        except:
            pass

    if port == 0:
        (h, port) = server.socket.getsockname()

        print(port)
        print(client_port)


    sys.stderr.write(interpreter.get_greeting_msg())
    sys.stderr.flush()

    interpreter.server = server
    server.serve_forever()

    return server


def StartServer(host, port, client_port):
    #replace exit (see comments on method)
    #note that this does not work in jython!!! (sys method can't be replaced).
    sys.exit = _DoExit

    interpreter = InterpreterInterface(host, client_port, threading.currentThread())

    server_thread = threading.Thread(target=start_server,
                                     name='ServerThread',
                                     args=(host, port, interpreter))
    server_thread.setDaemon(True)
    server_thread.start()

    process_exec_queue(interpreter)


def get_interpreter():
    try:
        interpreterInterface = getattr(__builtin__, 'interpreter')
    except AttributeError:
        interpreterInterface = InterpreterInterface(None, None, threading.currentThread())
        setattr(__builtin__, 'interpreter', interpreterInterface)

    return interpreterInterface


def get_completions(text, token, globals, locals):
    interpreterInterface = get_interpreter()

    interpreterInterface.interpreter.update(globals, locals)

    return interpreterInterface.getCompletions(text, token)

def get_frame():
    interpreterInterface = get_interpreter()

    return interpreterInterface.getFrame()

def exec_code(code, globals, locals):
    interpreterInterface = get_interpreter()

    interpreterInterface.interpreter.update(globals, locals)

    res = interpreterInterface.needMore(code)

    if res:
        return True

    interpreterInterface.addExec(code)

    return False


def read_line(s):
    ret = ''

    while True:
        c = s.recv(1)

        if c == '\n' or c == '':
            break
        else:
            ret += c

    return ret

# Debugger integration

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

def consoleExec(thread_id, frame_id, expression):
    """returns 'False' in case expression is partialy correct
    """
    frame = pydevd_vars.findFrame(thread_id, frame_id)

    expression = str(expression.replace('@LINE@', '\n'))

    #Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    #(Names not resolved in generator expression in method)
    #See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = {}
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals) #locals later because it has precedence over the actual globals

    if IPYTHON:
        return exec_code(CodeFragment(expression), updated_globals, frame.f_locals)

    interpreter = ConsoleWriter()

    try:
        code = compile_command(expression)
    except (OverflowError, SyntaxError, ValueError):
        # Case 1
        interpreter.showsyntaxerror()
        return False

    if code is None:
        # Case 2
        return True

    #Case 3

    try:
        Exec(code, updated_globals, frame.f_locals)

    except SystemExit:
        raise
    except:
        interpreter.showtraceback()

    return False

#=======================================================================================================================
# main
#=======================================================================================================================


if __name__ == '__main__':
    port, client_port = sys.argv[1:3]
    import pydev_localhost

    if int(port) == 0 and int(client_port) == 0:
        (h, p) = pydev_localhost.get_socket_name()

        client_port = p

    StartServer(pydev_localhost.get_localhost(), int(port), int(client_port))
