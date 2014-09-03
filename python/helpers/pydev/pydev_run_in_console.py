
from pydevconsole import *

import pydev_imports


def run_file(file, globals=None, locals=None):
    if os.path.isdir(file):
        new_target = os.path.join(file, '__main__.py')
        if os.path.isfile(new_target):
            file = new_target

    if globals is None:
        # patch provided by: Scott Schlesier - when script is run, it does not
        # use globals from pydevd:
        # This will prevent the pydevd script from contaminating the namespace for the script to be debugged

        # pretend pydevd is not the main module, and
        # convince the file to be debugged that it was loaded as main
        sys.modules['pydevd'] = sys.modules['__main__']
        sys.modules['pydevd'].__name__ = 'pydevd'

        from imp import new_module
        m = new_module('__main__')
        sys.modules['__main__'] = m
        if hasattr(sys.modules['pydevd'], '__loader__'):
            setattr(m, '__loader__', getattr(sys.modules['pydevd'], '__loader__'))

        m.__file__ = file
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