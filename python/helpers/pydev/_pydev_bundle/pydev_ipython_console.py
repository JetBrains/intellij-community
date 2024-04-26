import sys
from _pydev_bundle.pydev_console_utils import BaseInterpreterInterface
from _pydev_bundle.pydev_ipython_console_011 import get_pydev_ipython_frontend
from _pydev_bundle.pydev_ipython_console_011 import get_ipython_hidden_vars

# Uncomment to force PyDev standard shell.
# raise ImportError()

#=======================================================================================================================
# IPythonInterpreterInterface
#=======================================================================================================================
class IPythonInterpreterInterface(BaseInterpreterInterface):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, main_thread, show_banner=True, connect_status_queue=None, rpc_client=None):
        BaseInterpreterInterface.__init__(self, main_thread, connect_status_queue, rpc_client)
        self.interpreter = get_pydev_ipython_frontend(rpc_client)
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
            more, exception_occurred = self.interpreter.add_exec(code_fragment.text)
        finally:
            if code_fragment.text.rstrip().endswith('??'):
                print('<--IPython')
        return bool(more), exception_occurred

    def get_namespace(self):
        return self.interpreter.get_namespace()

    def close(self):
        sys.exit(0)

    def notify_about_magic(self):
        if not self.notification_succeeded:
            self.notification_tries+=1
            if self.notification_tries>self.notification_max_tries:
                return
            completions = self.do_get_completions("%", "%")
            magic_commands = [x[0] for x in completions]

            server = self.get_server()

            if server is not None:
                try:
                    server.notifyAboutMagic(magic_commands, self.interpreter.is_automagic())
                    self.notification_succeeded = True
                except :
                    self.notification_succeeded = False

    def get_ipython_hidden_vars_dict(self):
        if hasattr(self.interpreter, 'ipython') and hasattr(self.interpreter.ipython, 'user_ns_hidden'):
            ipython_shell = self.interpreter.ipython
            return get_ipython_hidden_vars(ipython_shell)

    def notify_first_command_executed(self):
        self.interpreter.ipython.patch_stdout_if_needed()
