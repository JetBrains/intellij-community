import sys
import traceback

from _pydev_bundle.pydev_code_executor import BaseCodeExecutor
from _pydev_bundle.pydev_ipython_console_011 import get_pydev_ipython_frontend, PyDebuggerTerminalInteractiveShell, get_ipython_hidden_vars
from _pydevd_bundle.pydevd_constants import dict_iter_items
from IPython.core.interactiveshell import InteractiveShell


# Uncomment to force PyDev standard shell.
# raise ImportError()

# TODO reuse `CodeExecutor` in `InterpreterInterface` in pydev_ipython_console.py
#=======================================================================================================================
# IPythonCodeExecutor
#=======================================================================================================================
class IPythonCodeExecutor(BaseCodeExecutor):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, show_banner=True, rpc_client=None):
        super(IPythonCodeExecutor, self).__init__()

        self.is_jupyter_debugger_shell = False
        # checking if it's not a Jupyter Debug Session
        ipython = self._get_ipython_or_none()
        if ipython is None or not hasattr(ipython, 'debugger'):
            self.interpreter = get_pydev_ipython_frontend(rpc_client)
        else:
            self.is_jupyter_debugger_shell = True
            self.original_interactive_shell_instance = InteractiveShell._instance
            self.interpreter = get_pydev_ipython_frontend(rpc_client, is_jupyter_debugger=True)
        self._input_error_printed = False
        self.notification_succeeded = False
        self.notification_tries = 0
        self.notification_max_tries = 3
        self.show_banner = show_banner

    def get_greeting_msg(self):
        return self.interpreter.get_greeting_msg()

    def do_add_exec(self, code_fragment):
        self.notify_about_magic()
        if code_fragment.text.rstrip().endswith('??'):
            print('IPython-->')
        try:
            if self.is_jupyter_debugger_shell:
                self.interpreter.ipython.execution_count = self.original_interactive_shell_instance.execution_count
                InteractiveShell._instance = PyDebuggerTerminalInteractiveShell.new_instance
                more, exception_occurred = self.interpreter.add_exec(code_fragment.text)
                self.interpreter.ipython.execution_count += 1
                self.original_interactive_shell_instance.execution_count = self.interpreter.ipython.execution_count
            else:
                more, exception_occurred = self.interpreter.add_exec(code_fragment.text)
        finally:
            if self.is_jupyter_debugger_shell:
                InteractiveShell._instance = self.original_interactive_shell_instance
            if code_fragment.text.rstrip().endswith('??'):
                print('<--IPython')
        return bool(more), exception_occurred

    def get_namespace(self):
        return self.interpreter.get_namespace()

    def close(self):
        sys.exit(0)

    def notify_about_magic(self):
        pass

    def get_ipython_hidden_vars_dict(self):
        if hasattr(self.interpreter, 'ipython'):
            shell_ns_hidden_dict = get_ipython_hidden_vars(self.interpreter.ipython)
            global_ns_hidden_dict = None
            if self.is_jupyter_debugger_shell:
                try:
                    ipython = self._get_ipython_or_none()
                    if ipython is not None:
                        global_ns_hidden_dict = get_ipython_hidden_vars(ipython)
                except:
                    pass

                if global_ns_hidden_dict is not None:
                    shell_ns_hidden_dict.update(global_ns_hidden_dict)

            return shell_ns_hidden_dict

    def _get_ipython_or_none(self):
        ipython = None

        try:
            ipython = get_ipython()
        except:
            pass

        return ipython
