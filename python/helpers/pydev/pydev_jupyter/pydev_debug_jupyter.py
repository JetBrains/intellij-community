
import sys
import traceback
from _pydevd_bundle.pydevd_constants import dict_iter_items


def update_filenames(debugger, new_name):
    if hasattr(debugger, "ipnb_breakpoints"):
        debugger.ipnb_breakpoints[new_name] = {}

        for file, breakpoints in dict_iter_items(debugger.ipnb_breakpoints):
            for line, breakpoint in dict_iter_items(breakpoints):
                if breakpoint.update_cell_file:
                    breakpoint.cell_file = new_name
                    breakpoint.update_cell_file = False
                    debugger.ipnb_cell_to_file[new_name] = breakpoint.file


def compile_cache_wrapper(orig, ipython_shell):
    def compile_cache(*args, **kwargs):
        cache_name = orig(*args, **kwargs)
        update_filenames(ipython_shell.debugger, cache_name)
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


def update_bp_filenames():
    ipython_shell = get_ipython()
    debugger = ipython_shell.debugger

    if hasattr(debugger, "ipnb_breakpoints"):
        for file, breakpoints in dict_iter_items(debugger.ipnb_breakpoints):
            for line, breakpoint in dict_iter_items(breakpoints):
                breakpoint.update_cell_file = True
