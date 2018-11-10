import sys
import traceback

from _pydev_bundle.pydev_code_executor import BaseCodeExecutor
from _pydev_bundle.pydev_ipython_console_011 import get_pydev_frontend
from _pydevd_bundle.pydevd_constants import dict_iter_items


# Uncomment to force PyDev standard shell.
# raise ImportError()

# TODO reuse `CodeExecutor` in `InterpreterInterface` in pydev_ipython_console.py
#=======================================================================================================================
# CodeExecutor
#=======================================================================================================================
class CodeExecutor(BaseCodeExecutor):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, show_banner=True, rpc_client=None):
        super(CodeExecutor, self).__init__()

        self.interpreter = get_pydev_frontend(rpc_client)
        self._input_error_printed = False
        self.notification_succeeded = False
        self.notification_tries = 0
        self.notification_max_tries = 3
        self.show_banner = show_banner

    def get_greeting_msg(self):
        if self.show_banner:
            self.interpreter.show_banner()
        return self.interpreter.get_greeting_msg()

    def do_add_exec(self, code_fragment):
        self.notify_about_magic()
        if code_fragment.text.rstrip().endswith('??'):
            print('IPython-->')
        try:
            res = bool(self.interpreter.add_exec(code_fragment.text))
        finally:
            if code_fragment.text.rstrip().endswith('??'):
                print('<--IPython')
        return res

    def get_namespace(self):
        return self.interpreter.get_namespace()

    def close(self):
        sys.exit(0)

    def notify_about_magic(self):
        pass

    def get_ipython_hidden_vars_dict(self):
        try:
            if hasattr(self.interpreter, 'ipython') and hasattr(self.interpreter.ipython, 'user_ns_hidden'):
                user_ns_hidden = self.interpreter.ipython.user_ns_hidden
                if isinstance(user_ns_hidden, dict):
                    # Since IPython 2 dict `user_ns_hidden` contains hidden variables and values
                    user_hidden_dict = user_ns_hidden.copy()
                else:
                    # In IPython 1.x `user_ns_hidden` used to be a set with names of hidden variables
                    user_hidden_dict = dict([(key, val) for key, val in dict_iter_items(self.interpreter.ipython.user_ns)
                                             if key in user_ns_hidden])

                # while `_`, `__` and `___` were not initialized, they are not presented in `user_ns_hidden`
                user_hidden_dict.setdefault('_', '')
                user_hidden_dict.setdefault('__', '')
                user_hidden_dict.setdefault('___', '')

                return user_hidden_dict
        except:
            # Getting IPython variables shouldn't break loading frame variables
            traceback.print_exc()
