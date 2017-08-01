import sys
from _pydev_bundle.pydev_console_utils import BaseInterpreterInterface

import os
import traceback

# Uncomment to force PyDev standard shell.
# raise ImportError()

from _pydev_bundle.pydev_ipython_console_011 import get_pydev_frontend
from _pydevd_bundle.pydevd_constants import dict_iter_items
from _pydevd_bundle.pydevd_io import IOBuf

#=======================================================================================================================
# InterpreterInterface
#=======================================================================================================================
class InterpreterInterface(BaseInterpreterInterface):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, host, client_port, mainThread, show_banner=True):
        BaseInterpreterInterface.__init__(self, mainThread)
        self.client_port = client_port
        self.host = host

        # Wrap output to handle IPython's banner and show it in appropriate time
        original_stdout = sys.stdout
        sys.stdout = IOBuf()
        self.interpreter = get_pydev_frontend(host, client_port, show_banner=show_banner)
        self.default_banner = sys.stdout.getvalue()
        sys.stdout = original_stdout

        self._input_error_printed = False
        self.notification_succeeded = False
        self.notification_tries = 0
        self.notification_max_tries = 3


    def get_greeting_msg(self):
        return self.interpreter.get_greeting_msg() + "\n" + self.default_banner

    def do_add_exec(self, codeFragment):
        self.notify_about_magic()
        if (codeFragment.text.rstrip().endswith('??')):
            print('IPython-->')
        try:
            res = bool(self.interpreter.add_exec(codeFragment.text))
        finally:
            if (codeFragment.text.rstrip().endswith('??')):
                print('<--IPython')

        return res


    def get_namespace(self):
        return self.interpreter.get_namespace()


    def getCompletions(self, text, act_tok):
        return self.interpreter.getCompletions(text, act_tok)

    def close(self):
        sys.exit(0)

    def notify_about_magic(self):
        if not self.notification_succeeded:
            self.notification_tries+=1
            if self.notification_tries>self.notification_max_tries:
                return
            completions = self.getCompletions("%", "%")
            magic_commands = [x[0] for x in completions]

            server = self.get_server()

            if server is not None:
                try:
                    server.NotifyAboutMagic(magic_commands, self.interpreter.is_automagic())
                    self.notification_succeeded = True
                except :
                    self.notification_succeeded = False

    def get_ipython_hidden_vars_dict(self):
        try:
            useful_ipython_vars = ['_', '__']
            if hasattr(self.interpreter, 'ipython') and hasattr(self.interpreter.ipython, 'user_ns_hidden'):
                user_ns_hidden = self.interpreter.ipython.user_ns_hidden
                if isinstance(user_ns_hidden, dict):
                    # Since IPython 2 dict `user_ns_hidden` contains hidden variables and values
                    user_hidden_dict = user_ns_hidden
                else:
                    # In IPython 1.x `user_ns_hidden` used to be a set with names of hidden variables
                    user_hidden_dict = dict([(key, val) for key, val in dict_iter_items(self.interpreter.ipython.user_ns)
                                             if key in user_ns_hidden])
                return dict([(key, val) for key, val in dict_iter_items(user_hidden_dict) if key not in useful_ipython_vars])
        except:
            # Getting IPython variables shouldn't break loading frame variables
            traceback.print_exc()

