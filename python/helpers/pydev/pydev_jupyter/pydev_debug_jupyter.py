
import sys
import traceback
from _pydevd_bundle.pydevd_constants import dict_iter_items


def update_cell_name(pydb, new_name):
    if hasattr(pydb, "latest_hash"):
        latest_hash = pydb.latest_hash
        if not hasattr(pydb, "jupyter_hash_to_cell"):
            pydb.jupyter_hash_to_cell = {}
        pydb.jupyter_hash_to_cell[latest_hash] = new_name
        pydb.jupyter_cell_to_hash[new_name] = latest_hash
        # if hasattr(pydb, "jupyter_breakpoints"):
        #     line_to_breakpoint = pydb.jupyter_breakpoints[latest_hash]
        #     for line, bp in dict_iter_items(line_to_breakpoint):
        #         bp.cell_file = new_name


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


def set_latest_hash(latest_hash):
    ipython_shell = get_ipython()
    ipython_shell.debugger.latest_hash = latest_hash
