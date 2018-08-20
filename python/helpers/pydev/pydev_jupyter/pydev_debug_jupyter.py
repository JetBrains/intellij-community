
import sys
import traceback


def attach_to_debugger(debugger_port):
    ipython_shell = get_ipython()

    # Try to import the packages needed to attach the debugger
    import pydevd
    from _pydev_bundle import pydev_localhost

    ipython_shell.debugger = pydevd.PyDB()
    try:
        ipython_shell.debugger.connect(pydev_localhost.get_localhost(), debugger_port)
        ipython_shell.debugger.prepare_to_run()
        from _pydevd_bundle import pydevd_tracing
    except:
        traceback.print_exc()
        sys.stderr.write('Failed to connect to target debugger.\n')

    # Register to process commands when idle
    try:
        import pydevconsole
        pydevconsole.set_debug_hook(ipython_shell.debugger.process_internal_commands)
    except:
        traceback.print_exc()
        sys.stderr.write('Version of Python does not support debuggable Interactive Console.\n')