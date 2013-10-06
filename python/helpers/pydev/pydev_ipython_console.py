import sys
from pydev_console_utils import BaseInterpreterInterface
import re

import os

os.environ['TERM'] = 'emacs' #to use proper page_more() for paging


#Uncomment to force PyDev standard shell.   
#raise ImportError()

try:
    #IPython 0.11 broke compatibility...
    from pydev_ipython_console_011 import PyDevFrontEnd
except:
    from pydev_ipython_console_010 import PyDevFrontEnd

#=======================================================================================================================
# InterpreterInterface
#=======================================================================================================================
class InterpreterInterface(BaseInterpreterInterface):
    '''
        The methods in this class should be registered in the xml-rpc server.
    '''

    def __init__(self, host, client_port, mainThread):
        BaseInterpreterInterface.__init__(self, mainThread)
        self.client_port = client_port
        self.host = host
        self.interpreter = PyDevFrontEnd()
        self._input_error_printed = False
        self.notification_succeeded = False
        self.notification_tries = 0
        self.notification_max_tries = 3

        self.notify_about_magic()

    def get_greeting_msg(self):
        return self.interpreter.get_greeting_msg()

    def doAddExec(self, line):
        self.notify_about_magic()
        if (line.rstrip().endswith('??')):
            print('IPython-->')
        try:
            res = bool(self.interpreter.addExec(line))
        finally:
            if (line.rstrip().endswith('??')):
                print('<--IPython')

        return res


    def getNamespace(self):
        return self.interpreter.getNamespace()


    def getCompletions(self, text, act_tok):
        try:
            ipython_completion = text.startswith('%')
            if not ipython_completion:
                s = re.search(r'\bcd\b', text)
                if s is not None and s.start() == 0:
                    ipython_completion = True

            if text is None:
                text = ""

            TYPE_LOCAL = '9'
            _line, completions = self.interpreter.complete(text)

            ret = []
            append = ret.append
            for completion in completions:
                if completion.startswith('%'):
                    append((completion[1:], '', '%', TYPE_LOCAL))
                else:
                    append((completion, '', '', TYPE_LOCAL))

            if ipython_completion:
                return ret

            #Otherwise, use the default PyDev completer (to get nice icons)
            from _completer import Completer

            completer = Completer(self.getNamespace(), None)
            completions = completer.complete(act_tok)
            cset = set()
            for c in completions:
                cset.add(c[0])
            for c in ret:
                if c[0] not in cset:
                    completions.append(c)

            return completions

        except:
            import traceback

            traceback.print_exc()
            return []

    def close(self):
        sys.exit(0)

    def ipython_editor(self, file, line):
        server = self.get_server()

        if server is not None:
            return server.IPythonEditor(os.path.realpath(file), line)

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




