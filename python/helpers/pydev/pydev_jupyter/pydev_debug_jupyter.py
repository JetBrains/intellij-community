
import sys
import traceback


def update_cell_name(pydb, new_name):
    if hasattr(pydb, "latest_cell_id"):
        latest_cell_id = pydb.latest_cell_id
        pydb.jupyter_cell_id_to_name[latest_cell_id] = new_name
        pydb.jupyter_cell_name_to_id[new_name] = latest_cell_id
        # update breakpoints?


def compile_cache_wrapper(orig, ipython_shell):
    def compile_cache(*args, **kwargs):
        cache_name = orig(*args, **kwargs)
        update_cell_name(ipython_shell.debugger, cache_name)
        return cache_name
    return compile_cache


def patch_compile_cache(ipython_shell):
    ipython_shell.compile.cache = compile_cache_wrapper(ipython_shell.compile.cache, ipython_shell)


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

    patch_compile_cache(ipython_shell)

    # Register to process commands when idle
    try:
        import pydevconsole
        pydevconsole.set_debug_hook(ipython_shell.debugger.process_internal_commands)
    except:
        traceback.print_exc()
        sys.stderr.write('Version of Python does not support debuggable Interactive Console.\n')


def set_latest_cell_id(latest_cell_id):
    ipython_shell = get_ipython()
    ipython_shell.debugger.latest_cell_id = latest_cell_id
    if not hasattr(ipython_shell.debugger, 'jupyter_cell_id_to_name'):
        ipython_shell.debugger.jupyter_cell_name_to_id = {}
        ipython_shell.debugger.jupyter_cell_id_to_name = {}
