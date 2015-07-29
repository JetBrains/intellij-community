from pydev_imports import xmlrpclib, _queue, Exec
import sys
from pydevd_constants import IS_JYTHON
from _pydev_imps import _pydev_thread as thread
import pydevd_xml
import pydevd_vars
from pydevd_utils import *  # @UnusedWildImport
import traceback

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

    def close(self, *args, **kwargs):
        pass #expected in StdIn


class CodeFragment:
    def __init__(self, text, is_single_line=True):
        self.text = text
        self.is_single_line = is_single_line

    def append(self, code_fragment):
        self.text = self.text + "\n" + code_fragment.text
        if not code_fragment.is_single_line:
            self.is_single_line = False

#=======================================================================================================================
# BaseInterpreterInterface
#=======================================================================================================================
class BaseInterpreterInterface:
    def __init__(self, mainThread):
        self.mainThread = mainThread
        self.interruptable = False
        self.exec_queue = _queue.Queue(0)
        self.buffer = None

    def needMoreForCode(self, source):
        if hasattr(self.interpreter, 'is_complete'):
            return not self.interpreter.is_complete(source)
        try:
            code = self.interpreter.compile(source, '<input>', 'exec')
        except (OverflowError, SyntaxError, ValueError):
            # Case 1
            return False
        if code is None:
            # Case 2
            return True

        # Case 3
        return False

    def needMore(self, code_fragment):
        if self.buffer is None:
            self.buffer = code_fragment
        else:
            self.buffer.append(code_fragment)

        return self.needMoreForCode(self.buffer.text)

    def createStdIn(self):
        return StdIn(self, self.host, self.client_port)

    def addExec(self, code_fragment):
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
            sys.stdin = self.createStdIn()
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
                            traceback.print_exc()

                try:
                    self.startExec()
                    if hasattr(self, 'debugger'):
                        import pydevd_tracing
                        pydevd_tracing.SetTrace(self.debugger.trace_dispatch)

                    more = self.doAddExec(code_fragment)

                    if hasattr(self, 'debugger'):
                        import pydevd_tracing
                        pydevd_tracing.SetTrace(None)

                    self.finishExec(more)
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
            traceback.print_exc()

        return more


    def doAddExec(self, codeFragment):
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

                        import _pydev_jy_imports_tipper

                        is_method, infos = _pydev_jy_imports_tipper.ismethod(obj)
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


    def doExecCode(self, code, is_single_line):
        try:
            code_fragment = CodeFragment(code, is_single_line)
            more = self.needMore(code_fragment)
            if not more:
                code_fragment = self.buffer
                self.buffer = None
                self.exec_queue.put(code_fragment)

            return more
        except:
            traceback.print_exc()
            return False

    def execLine(self, line):
        return self.doExecCode(line, True)


    def execMultipleLines(self, lines):
        if IS_JYTHON:
            for line in lines.split('\n'):
                self.doExecCode(line, True)
        else:
            return self.doExecCode(lines, False)


    def interrupt(self):
        self.buffer = None # Also clear the buffer when it's interrupted.
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
        if getattr(self, 'host', None) is not None:
            return xmlrpclib.Server('http://%s:%s' % (self.host, self.client_port))
        else:
            return None

    server = property(get_server)

    def finishExec(self, more):
        self.interruptable = False

        server = self.get_server()

        if server is not None:
            return server.NotifyFinished(more)
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

    def getArray(self, attr, roffset, coffset, rows, cols, format):
        xml = "<xml>"
        name = attr.split("\t")[-1]
        array = pydevd_vars.evalInContext(name, self.getNamespace(), self.getNamespace())

        array, metaxml, r, c, f = pydevd_vars.array_to_meta_xml(array, name, format)
        xml += metaxml
        format = '%' + f
        if rows == -1 and cols == -1:
            rows = r
            cols = c
        xml += pydevd_vars.array_to_xml(array, roffset, coffset, rows, cols, format)
        xml += "</xml>"

        return xml

    def evaluate(self, expression):
        xml = "<xml>"
        result = pydevd_vars.evalInContext(expression, self.getNamespace(), self.getNamespace())

        xml += pydevd_vars.varToXML(result, expression)

        xml += "</xml>"

        return xml

    def changeVariable(self, attr, value):
        def do_change_variable():
            Exec('%s=%s' % (attr, value), self.getNamespace(), self.getNamespace())

        # Important: it has to be really enabled in the main thread, so, schedule
        # it to run in the main thread.
        self.exec_queue.put(do_change_variable)

    def _findFrame(self, thread_id, frame_id):
        '''
        Used to show console with variables connection.
        Always return a frame where the locals map to our internal namespace.
        '''
        VIRTUAL_FRAME_ID = "1" # matches PyStackFrameConsole.java
        VIRTUAL_CONSOLE_ID = "console_main" # matches PyThreadConsole.java
        if thread_id == VIRTUAL_CONSOLE_ID and frame_id == VIRTUAL_FRAME_ID:
            f = FakeFrame()
            f.f_globals = {} #As globals=locals here, let's simply let it empty (and save a bit of network traffic).
            f.f_locals = self.getNamespace()
            return f
        else:
            return self.orig_findFrame(thread_id, frame_id)

    def connectToDebugger(self, debuggerPort):
        '''
        Used to show console with variables connection.
        Mainly, monkey-patches things in the debugger structure so that the debugger protocol works.
        '''
        def do_connect_to_debugger():
            try:
                # Try to import the packages needed to attach the debugger
                import pydevd
                import _pydev_threading as threading

            except:
                # This happens on Jython embedded in host eclipse
                traceback.print_exc()
                sys.stderr.write('pydevd is not available, cannot connect\n',)

            import pydev_localhost
            threading.currentThread().__pydevd_id__ = "console_main"

            self.orig_findFrame = pydevd_vars.findFrame
            pydevd_vars.findFrame = self._findFrame

            self.debugger = pydevd.PyDB()
            try:
                self.debugger.connect(pydev_localhost.get_localhost(), debuggerPort)
                self.debugger.prepareToRun()
                import pydevd_tracing
                pydevd_tracing.SetTrace(None)
            except:
                traceback.print_exc()
                sys.stderr.write('Failed to connect to target debugger.\n')

            # Register to process commands when idle
            self.debugrunning = False
            try:
                import pydevconsole
                pydevconsole.set_debug_hook(self.debugger.processInternalCommands)
            except:
                traceback.print_exc()
                sys.stderr.write('Version of Python does not support debuggable Interactive Console.\n')

        # Important: it has to be really enabled in the main thread, so, schedule
        # it to run in the main thread.
        self.exec_queue.put(do_connect_to_debugger)

        return ('connect complete',)

    def hello(self, input_str):
        # Don't care what the input string is
        return ("Hello eclipse",)

    def enableGui(self, guiname):
        ''' Enable the GUI specified in guiname (see inputhook for list).
            As with IPython, enabling multiple GUIs isn't an error, but
            only the last one's main loop runs and it may not work
        '''
        def do_enable_gui():
            from pydev_versioncheck import versionok_for_gui
            if versionok_for_gui():
                try:
                    from pydev_ipython.inputhook import enable_gui
                    enable_gui(guiname)
                except:
                    sys.stderr.write("Failed to enable GUI event loop integration for '%s'\n" % guiname)
                    traceback.print_exc()
            elif guiname not in ['none', '', None]:
                # Only print a warning if the guiname was going to do something
                sys.stderr.write("PyDev console: Python version does not support GUI event loop integration for '%s'\n" % guiname)
            # Return value does not matter, so return back what was sent
            return guiname

        # Important: it has to be really enabled in the main thread, so, schedule
        # it to run in the main thread.
        self.exec_queue.put(do_enable_gui)

#=======================================================================================================================
# FakeFrame
#=======================================================================================================================
class FakeFrame:
    '''
    Used to show console with variables connection.
    A class to be used as a mock of a frame.
    '''