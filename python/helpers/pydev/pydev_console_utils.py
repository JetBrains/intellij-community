from pydev_imports import xmlrpclib
import sys

import traceback

from pydevd_constants import USE_LIB_COPY

try:
    if USE_LIB_COPY:
        import _pydev_Queue as _queue
    else:
        import Queue as _queue
except:
    import queue as _queue

try:
    from pydevd_exec import Exec
except:
    from pydevd_exec2 import Exec

try:
    if USE_LIB_COPY:
        import _pydev_thread as thread
    else:
        import thread
except:
    import _thread as thread

import pydevd_xml
import pydevd_vars

from pydevd_utils import *

#=======================================================================================================================
# Null
#=======================================================================================================================
class Null:
    """
    Gotten from: http://aspn.activestate.com/ASPN/Cookbook/Python/Recipe/68205
    """

    def __init__(self, *args, **kwargs):
        return None

    def __call__(self, *args, **kwargs):
        return self

    def __getattr__(self, mname):
        return self

    def __setattr__(self, name, value):
        return self

    def __delattr__(self, name):
        return self

    def __repr__(self):
        return "<Null>"

    def __str__(self):
        return "Null"

    def __len__(self):
        return 0

    def __getitem__(self):
        return self

    def __setitem__(self, *args, **kwargs):
        pass

    def write(self, *args, **kwargs):
        pass

    def __nonzero__(self):
        return 0


#=======================================================================================================================
# BaseStdIn
#=======================================================================================================================
class BaseStdIn:
    def __init__(self, *args, **kwargs):
        try:
            self.encoding = sys.stdin.encoding
        except:
            #Not sure if it's available in all Python versions...
            pass

    def readline(self, *args, **kwargs):
        #sys.stderr.write('Cannot readline out of the console evaluation\n') -- don't show anything
        #This could happen if the user had done input('enter number).<-- upon entering this, that message would appear,
        #which is not something we want.
        return '\n'

    def isatty(self):
        return False #not really a file

    def write(self, *args, **kwargs):
        pass #not available StdIn (but it can be expected to be in the stream interface)

    def flush(self, *args, **kwargs):
        pass #not available StdIn (but it can be expected to be in the stream interface)

    def read(self, *args, **kwargs):
        #in the interactive interpreter, a read and a readline are the same.
        return self.readline()

#=======================================================================================================================
# StdIn
#=======================================================================================================================
class StdIn(BaseStdIn):
    '''
        Object to be added to stdin (to emulate it as non-blocking while the next line arrives)
    '''

    def __init__(self, interpreter, host, client_port):
        BaseStdIn.__init__(self)
        self.interpreter = interpreter
        self.client_port = client_port
        self.host = host

    def readline(self, *args, **kwargs):
        #Ok, callback into the client to get the new input
        try:
            server = xmlrpclib.Server('http://%s:%s' % (self.host, self.client_port))
            requested_input = server.RequestInput()
            if not requested_input:
                return '\n' #Yes, a readline must return something (otherwise we can get an EOFError on the input() call).
            return requested_input
        except:
            return '\n'


#=======================================================================================================================
# BaseInterpreterInterface
#=======================================================================================================================
class BaseInterpreterInterface:
    def __init__(self, mainThread):
        self.mainThread = mainThread
        self.interruptable = False
        self.exec_queue = _queue.Queue(0)
        self.buffer = []

    def needMore(self, buffer, line):
        if not buffer:
            buffer = []
        buffer.append(line)
        source = "\n".join(buffer)
        if hasattr(self.interpreter, 'is_complete'):
            return not self.interpreter.is_complete(source)

        try:
            code = self.interpreter.compile(source, "<input>", "single")
        except (OverflowError, SyntaxError, ValueError):
            # Case 1
            return False

        if code is None:
            # Case 2
            return True

        # Case 3
        return False


    def addExec(self, line):
        #f_opened = open('c:/temp/a.txt', 'a')
        #f_opened.write(line+'\n')
        original_in = sys.stdin
        try:
            help = None
            if 'pydoc' in sys.modules:
                pydoc = sys.modules['pydoc'] #Don't import it if it still is not there.

                if hasattr(pydoc, 'help'):
                    #You never know how will the API be changed, so, let's code defensively here
                    help = pydoc.help
                    if not hasattr(help, 'input'):
                        help = None
        except:
            #Just ignore any error here
            pass

        more = False
        try:
            sys.stdin = StdIn(self, self.host, self.client_port)
            try:
                if help is not None:
                    #This will enable the help() function to work.
                    try:
                        try:
                            help.input = sys.stdin
                        except AttributeError:
                            help._input = sys.stdin
                    except:
                        help = None
                        if not self._input_error_printed:
                            self._input_error_printed = True
                            sys.stderr.write('\nError when trying to update pydoc.help.input\n')
                            sys.stderr.write('(help() may not work -- please report this as a bug in the pydev bugtracker).\n\n')
                            import traceback;

                            traceback.print_exc()

                try:
                    self.startExec()
                    more = self.doAddExec(line)
                    self.finishExec()
                finally:
                    if help is not None:
                        try:
                            try:
                                help.input = original_in
                            except AttributeError:
                                help._input = original_in
                        except:
                            pass

            finally:
                sys.stdin = original_in
        except SystemExit:
            raise
        except:
            import traceback;

            traceback.print_exc()

        #it's always false at this point
        need_input = False
        return more, need_input


    def doAddExec(self, line):
        '''
        Subclasses should override.
        
        @return: more (True if more input is needed to complete the statement and False if the statement is complete).
        '''
        raise NotImplementedError()


    def getNamespace(self):
        '''
        Subclasses should override.
        
        @return: dict with namespace.
        '''
        raise NotImplementedError()


    def getDescription(self, text):
        try:
            obj = None
            if '.' not in text:
                try:
                    obj = self.getNamespace()[text]
                except KeyError:
                    return ''

            else:
                try:
                    splitted = text.split('.')
                    obj = self.getNamespace()[splitted[0]]
                    for t in splitted[1:]:
                        obj = getattr(obj, t)
                except:
                    return ''

            if obj is not None:
                try:
                    if sys.platform.startswith("java"):
                        #Jython
                        doc = obj.__doc__
                        if doc is not None:
                            return doc

                        import jyimportsTipper

                        is_method, infos = jyimportsTipper.ismethod(obj)
                        ret = ''
                        if is_method:
                            for info in infos:
                                ret += info.getAsDoc()
                            return ret

                    else:
                        #Python and Iron Python
                        import inspect #@UnresolvedImport

                        doc = inspect.getdoc(obj)
                        if doc is not None:
                            return doc
                except:
                    pass

            try:
                #if no attempt succeeded, try to return repr()... 
                return repr(obj)
            except:
                try:
                    #otherwise the class 
                    return str(obj.__class__)
                except:
                    #if all fails, go to an empty string 
                    return ''
        except:
            traceback.print_exc()
            return ''


    def execLine(self, line):
        try:
            #buffer = self.interpreter.buffer[:]
            self.exec_queue.put(line)
            return self.needMore(self.buffer, line)
        except:
            traceback.print_exc()
            return False


    def interrupt(self):
        try:
            if self.interruptable:
                if hasattr(thread, 'interrupt_main'): #Jython doesn't have it
                    thread.interrupt_main()
                else:
                    self.mainThread._thread.interrupt() #Jython
            return True
        except:
            traceback.print_exc()
            return False

    def close(self):
        sys.exit(0)

    def startExec(self):
        self.interruptable = True

    def get_server(self):
        if self.host is not None:
            return xmlrpclib.Server('http://%s:%s' % (self.host, self.client_port))
        else:
            return None

    def finishExec(self):
        self.interruptable = False

        server = self.get_server()

        if server is not None:
            return server.NotifyFinished()
        else:
            return True

    def getFrame(self):
        xml = "<xml>"
        xml += pydevd_xml.frameVarsToXML(self.getNamespace())
        xml += "</xml>"

        return xml

    def getVariable(self, attributes):
        xml = "<xml>"
        valDict = pydevd_vars.resolveVar(self.getNamespace(), attributes)
        if valDict is None:
            valDict = {}

        keys = valDict.keys()

        for k in keys:
            xml += pydevd_vars.varToXML(valDict[k], to_string(k))

        xml += "</xml>"

        return xml

    def changeVariable(self, attr, value):
        Exec('%s=%s' % (attr, value), self.getNamespace(), self.getNamespace())