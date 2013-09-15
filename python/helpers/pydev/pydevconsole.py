try:
    from code import InteractiveConsole
except ImportError:
    from pydevconsole_code_for_ironpython import InteractiveConsole

from code import compile_command
from code import InteractiveInterpreter

import os
import sys

from pydevd_constants import USE_LIB_COPY
from pydevd_utils import *

if USE_LIB_COPY:
    import _pydev_threading as threading
else:
    import threading

import traceback
import fix_getpass
fix_getpass.fixGetpass()

import pydevd_vars

try:
    from pydevd_exec import Exec
except:
    from pydevd_exec2 import Exec

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

try:
    class ExecState:
        FIRST_CALL = True
        PYDEV_CONSOLE_RUN_IN_UI = False #Defines if we should run commands in the UI thread.

    from org.python.pydev.core.uiutils import RunInUiThread #@UnresolvedImport
    from java.lang import Runnable #@UnresolvedImport

    class Command(Runnable):
        def __init__(self, interpreter, line):
            self.interpreter = interpreter
            self.line = line

        def run(self):
            if ExecState.FIRST_CALL:
                ExecState.FIRST_CALL = False
                sys.stdout.write('\nYou are now in a console within Eclipse.\nUse it with care as it can halt the VM.\n')
                sys.stdout.write(
                    'Typing a line with "PYDEV_CONSOLE_TOGGLE_RUN_IN_UI"\nwill start executing all the commands in the UI thread.\n\n')

            if self.line == 'PYDEV_CONSOLE_TOGGLE_RUN_IN_UI':
                ExecState.PYDEV_CONSOLE_RUN_IN_UI = not ExecState.PYDEV_CONSOLE_RUN_IN_UI
                if ExecState.PYDEV_CONSOLE_RUN_IN_UI:
                    sys.stdout.write(
                        'Running commands in UI mode. WARNING: using sys.stdin (i.e.: calling raw_input()) WILL HALT ECLIPSE.\n')
                else:
                    sys.stdout.write('No longer running commands in UI mode.\n')
                self.more = False
            else:
                self.more = self.interpreter.push(self.line)


    def Sync(runnable):
        if ExecState.PYDEV_CONSOLE_RUN_IN_UI:
            return RunInUiThread.sync(runnable)
        else:
            return runnable.run()

except:
    #If things are not there, define a way in which there's no 'real' sync, only the default execution.
    class Command:
        def __init__(self, interpreter, line):
            self.interpreter = interpreter
            self.line = line

        def run(self):
            self.more = self.interpreter.push(self.line)

    def Sync(runnable):
        runnable.run()

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


    def doAddExec(self, line):
        command = Command(self.interpreter, line)
        Sync(command)
        return command.more


    def getNamespace(self):
        return self.namespace


    def getCompletions(self, text, act_tok):
        try:
            from _completer import Completer

            completer = Completer(self.namespace, None)
            return completer.complete(act_tok)
        except:
            import traceback

            traceback.print_exc()
            return []
        
    def close(self):
        sys.exit(0)

    def get_greeting_msg(self):
        return 'PyDev console: starting.\n'


def process_exec_queue(interpreter):
    while 1:
        try:
            try:
                line = interpreter.exec_queue.get(block=True, timeout=0.05)
            except _queue.Empty:
                continue

            if not interpreter.addExec(line):     #TODO: think about locks here
                interpreter.buffer = []
        except KeyboardInterrupt:
            interpreter.buffer = []
            continue
        except SystemExit:
            raise
        except:
            type, value, tb = sys.exc_info()
            traceback.print_exception(type, value, tb, file=sys.__stderr__)
            exit()


try:
    try:
        exitfunc = sys.exitfunc
    except AttributeError:
        exitfunc = None
    from pydev_ipython_console import InterpreterInterface

    IPYTHON = True
    if exitfunc is not None:
        sys.exitfunc = exitfunc

    else:
        try:
            delattr(sys, 'exitfunc')
        except:
            pass
except:
    IPYTHON = False
    #sys.stderr.write('PyDev console: started.\n')
    pass #IPython not available, proceed as usual.

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

    from pydev_imports import SimpleXMLRPCServer

    try:
        server = SimpleXMLRPCServer((host, port), logRequests=False, allow_none=True)

    except:
        sys.stderr.write('Error starting server with host: %s, port: %s, client_port: %s\n' % (host, port, client_port))
        raise

    server.register_function(interpreter.execLine)
    server.register_function(interpreter.getCompletions)
    server.register_function(interpreter.getFrame)
    server.register_function(interpreter.getVariable)
    server.register_function(interpreter.changeVariable)
    server.register_function(interpreter.getDescription)
    server.register_function(interpreter.close)
    server.register_function(interpreter.interrupt)
    server.register_function(handshake)

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
    return interpreterInterface.getFrame()

def exec_expression(expression, globals, locals):
    interpreterInterface = get_interpreter()

    interpreterInterface.interpreter.update(globals, locals)

    res = interpreterInterface.needMore(None, expression)

    if res:
        return True

    interpreterInterface.addExec(expression)

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
        return exec_expression(expression, updated_globals, frame.f_locals)

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
