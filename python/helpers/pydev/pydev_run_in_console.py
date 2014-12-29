
from pydevconsole import *

import pydev_imports
from pydevd_utils import save_main_module


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


    print('Running %s'%file)
    pydev_imports.execfile(file, globals, locals)  # execute the script

    return globals

#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    sys.stdin = BaseStdIn()
    port, client_port = sys.argv[1:3]

    del sys.argv[1]
    del sys.argv[1]

    file = sys.argv[1]

    del sys.argv[0]

    import pydev_localhost

    if int(port) == 0 and int(client_port) == 0:
        (h, p) = pydev_localhost.get_socket_name()

        client_port = p

    host = pydev_localhost.get_localhost()


    #replace exit (see comments on method)
    #note that this does not work in jython!!! (sys method can't be replaced).
    sys.exit = DoExit

    interpreter = InterpreterInterface(host, int(client_port), threading.currentThread())

    server_thread = threading.Thread(target=start_server,
                                     name='ServerThread',
                                     args=(host, int(port), interpreter))
    server_thread.setDaemon(True)
    server_thread.start()

    globals = run_file(file, None, None)

    interpreter.getNamespace().update(globals)

    process_exec_queue(interpreter)