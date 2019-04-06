from __future__ import print_function
import sys
import os
import subprocess
import traceback

def process_command_line(argv):
    setup = {}
    setup['port'] = 5678  # Default port for PyDev remote debugger
    setup['pid'] = 0
    setup['host'] = '127.0.0.1'

    i = 0
    while i < len(argv):
        if argv[i] == '--port':
            del argv[i]
            setup['port'] = int(argv[i])
            del argv[i]

        elif argv[i] == '--pid':
            del argv[i]
            setup['pid'] = int(argv[i])
            del argv[i]

        elif argv[i] == '--host':
            del argv[i]
            setup['host'] = int(argv[i])
            del argv[i]


    if not setup['pid']:
        sys.stderr.write('Expected --pid to be passed.\n')
        sys.exit(1)
    return setup

def main(setup):
    if sys.platform == 'linux':
        try:
            output = subprocess.check_output(['sysctl', 'kernel.yama.ptrace_scope'])
            if output.decode().strip()[-1] != '0':
                print("WARNING: The 'kernel.yama.ptrace_scope' parameter value is not 0, attach to process may not work correctly.\n"
                      "         Please run 'sudo sysctl kernel.yama.ptrace_scope=0' to change the value temporary\n"
                      "         or add the 'kernel.yama.ptrace_scope = 0' line to /etc/sysctl.d/10-ptrace.conf to set it permanently.",
                      file=sys.stderr)
        except Exception:
            traceback.print_exc()

    import add_code_to_python_process
    show_debug_info_on_target_process = 0

    pydevd_dirname = os.path.dirname(os.path.dirname(__file__))

    if sys.platform == 'win32':
        setup['pythonpath'] = pydevd_dirname.replace('\\', '/')
        setup['pythonpath2'] = os.path.dirname(__file__).replace('\\', '/')
        python_code = '''import sys;
sys.path.append("%(pythonpath)s");
sys.path.append("%(pythonpath2)s");
import attach_script;
attach_script.attach(port=%(port)s, host="%(host)s");
'''.replace('\r\n', '').replace('\r', '').replace('\n', '')
    else:
        setup['pythonpath'] = pydevd_dirname
        setup['pythonpath2'] = os.path.dirname(__file__)
        # We have to pass it a bit differently for gdb
        python_code = '''import sys;
sys.path.append(\\\"%(pythonpath)s\\\");
sys.path.append(\\\"%(pythonpath2)s\\\");
import attach_script;
attach_script.attach(port=%(port)s, host=\\\"%(host)s\\\");
'''.replace('\r\n', '').replace('\r', '').replace('\n', '')

    python_code = python_code % setup
    add_code_to_python_process.run_python_code(
        setup['pid'], python_code, connect_debugger_tracing=True, show_debug_info=show_debug_info_on_target_process)

if __name__ == '__main__':
    main(process_command_line(sys.argv[1:]))
