
import os
import sys
import traceback


def attach_to_debugger(debugger_port):
    debugger_options = {}
    env_key = "PYDEVD_EXTRA_ENVS"
    if env_key in debugger_options:
        for (env_name, value) in dict_iter_items(debugger_options[env_key]):
            existing_value = os.environ.get(env_name, None)
            if existing_value:
                os.environ[env_name] = "%s%c%s" % (existing_value, os.path.pathsep, value)
            else:
                os.environ[env_name] = value
            if env_name == "PYTHONPATH":
                sys.path.append(value)

        del debugger_options[env_key]

    ipython_shell = get_ipython()

    # Try to import the packages needed to attach the debugger
    import pydevd
    from _pydev_bundle import pydev_localhost

    ipython_shell.debugger = pydevd.PyDB()
    try:
        pydevd.apply_debugger_options(debugger_options)
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