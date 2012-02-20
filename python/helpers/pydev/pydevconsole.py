import socket

try:
    from code import InteractiveConsole
except ImportError:
    from pydevconsole_code_for_ironpython import InteractiveConsole

import os
import sys

import threading

import traceback

try:
    import Queue
except:
    import queue as Queue

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
        self.namespace = globals()
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
            import traceback;traceback.print_exc()
            return []


    def close(self):
        sys.exit(0)


def process_exec_queue(interpreter):
    while 1:
        try:
            try:
                line = interpreter.exec_queue.get(block=True, timeout=0.05)
            except Queue.Empty:
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
            try:
                print_exception()
                rpc.response_queue.put((seq, None))
            except:
                # Link didn't work, print same exception to __stderr__
                traceback.print_exception(type, value, tb, file=sys.__stderr__)
                exit()
            else:
                continue


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
        except :
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
    from pydev_imports import SimpleXMLRPCServer
    try:
        server = SimpleXMLRPCServer((host, port), logRequests=True)
    except:
        sys.stderr.write('Error starting server with host: %s, port: %s, client_port: %s\n' % (host, port, client_port))
        raise

    server.register_function(interpreter.execLine)
    server.register_function(interpreter.getCompletions)
    server.register_function(interpreter.getDescription)
    server.register_function(interpreter.close)
    server.register_function(interpreter.interrupt)
    server.register_function(handshake)

    if IPYTHON:
        try:
            interpreter.interpreter.ipython.hooks.editor = ipython_editor(interpreter)
        except :
            pass

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

    if not IPYTHON:
        sys.stderr.write('PyDev console: starting.\n')
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


def exec_expression(expression, globals, locals):

    interpreterInterface = get_interpreter()

    interpreterInterface.interpreter.update(globals, locals)

    res = interpreterInterface.needMore(None, expression)

    if res:
        return True


    interpreterInterface.addExec(expression)

    return False

#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    port, client_port = sys.argv[1:3]
    if port == 0 and client_port == 0:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.bind(('', 0))
        (h, p) = sock.getsockname()

        port = p

        sys.stdout.write(p)

        sock2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock2.bind(('', 0))
        (h, p) = sock2.getsockname()

        sys.stdout.write(p)

        client_port = p

    import pydev_localhost
    StartServer(pydev_localhost.get_localhost(), int(port), int(client_port))
    
