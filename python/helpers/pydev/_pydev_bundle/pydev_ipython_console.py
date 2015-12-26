import sys
from _pydev_bundle.pydev_console_utils import BaseInterpreterInterface

import os

os.environ['TERM'] = 'emacs' #to use proper page_more() for paging


# Uncomment to force PyDev standard shell.
# raise ImportError()

from _pydev_bundle.pydev_ipython_console_011 import get_pydev_frontend

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
        self.interpreter = get_pydev_frontend(host, client_port, show_banner=show_banner)
        self._input_error_printed = False
        self.notification_succeeded = False
        self.notification_tries = 0
        self.notification_max_tries = 3

        self.notify_about_magic()

    def get_greeting_msg(self):
        return self.interpreter.get_greeting_msg()

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




