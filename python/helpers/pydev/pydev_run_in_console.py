'''
Entry point module to run a file in the interactive console.
'''
import os
import sys
import traceback
from pydevconsole import do_exit, InterpreterInterface, process_exec_queue, start_console_server, init_mpl_in_console
from _pydev_imps._pydev_saved_modules import threading, _queue

from _pydev_bundle import pydev_imports
from _pydevd_bundle.pydevd_utils import save_main_module
from _pydev_bundle.pydev_console_utils import StdIn


def run_file(file, globals=None, locals=None):
    if os.path.isdir(file):
        new_target = os.path.join(file, '__main__.py')
        if os.path.isfile(new_target):
            file = new_target

    if globals is None:
        m = save_main_module(file, 'pydev_run_in_console')

        globals = m.__dict__
        try:
            globals['__builtins__'] = __builtins__
        except NameError:
            pass  # Not there on Jython...

    if locals is None:
        locals = globals

    sys.path.insert(0, os.path.split(file)[0])

    print('Running %s'%file)
    try:
        pydev_imports.execfile(file, globals, locals)  # execute the script
    except:
        traceback.print_exc()

    return globals

#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    port, client_port = sys.argv[1:3]

    del sys.argv[1]
    del sys.argv[1]

    file = sys.argv[1]

    del sys.argv[0]

    from _pydev_bundle import pydev_localhost

    if int(port) == 0 and int(client_port) == 0:
        (h, p) = pydev_localhost.get_socket_name()

        client_port = p

    host = pydev_localhost.get_localhost()


    #replace exit (see comments on method)
    #note that this does not work in jython!!! (sys method can't be replaced).
    sys.exit = do_exit

    connect_status_queue = _queue.Queue()
    interpreter = InterpreterInterface(host, int(client_port), threading.currentThread(), connect_status_queue=connect_status_queue)

    server_thread = threading.Thread(target=start_console_server,
                                     name='ServerThread',
                                     args=(host, int(port), interpreter))
    server_thread.setDaemon(True)
    server_thread.start()

    sys.stdin = StdIn(interpreter, host, client_port, sys.stdin)

    init_mpl_in_console(interpreter)

    try:
        success = connect_status_queue.get(True, 60)
        if not success:
            raise ValueError()
    except:
        sys.stderr.write("Console server didn't start\n")
        sys.stderr.flush()
        sys.exit(1)

    globals = run_file(file, None, None)

    interpreter.get_namespace().update(globals)

    interpreter.ShowConsole()

    process_exec_queue(interpreter)