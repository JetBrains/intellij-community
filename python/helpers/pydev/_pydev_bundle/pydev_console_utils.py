from _pydev_bundle.pydev_imports import xmlrpclib, _queue, Exec
import sys
from _pydevd_bundle.pydevd_constants import IS_JYTHON
from _pydev_imps import _pydev_thread as thread
from _pydevd_bundle import pydevd_xml
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_utils import *  # @UnusedWildImport
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

    def close(self, *args, **kwargs):
        pass #expected in StdIn


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

    def need_more_for_code(self, source):
        # PyDev-502: PyDev 3.9 F2 doesn't support backslash continuations

        # Strangely even the IPython console is_complete said it was complete
        # even with a continuation char at the end.
        if source.endswith('\\'):
            return True

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

    def need_more(self, code_fragment):
        if self.buffer is None:
            self.buffer = code_fragment
        else:
            self.buffer.append(code_fragment)

        return self.need_more_for_code(self.buffer.text)

    def create_std_in(self):
        return StdIn(self, self.host, self.client_port)

    def add_exec(self, code_fragment):
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
            sys.stdin = self.create_std_in()
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
                    self.start_exec()
                    if hasattr(self, 'debugger'):
                        from _pydevd_bundle import pydevd_tracing
                        pydevd_tracing.SetTrace(self.debugger.trace_dispatch)

                    more = self.do_add_exec(code_fragment)

                    if hasattr(self, 'debugger'):
                        from _pydevd_bundle import pydevd_tracing
                        pydevd_tracing.SetTrace(None)

                    self.finish_exec(more)
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


    def do_add_exec(self, codeFragment):
        '''
        Subclasses should override.

        @return: more (True if more input is needed to complete the statement and False if the statement is complete).
        '''
        raise NotImplementedError()


    def get_namespace(self):
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
                    obj = self.get_namespace()[text]
                except KeyError:
                    return ''

            else:
                try:
                    splitted = text.split('.')
                    obj = self.get_namespace()[splitted[0]]
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

                        from _pydev_bundle import _pydev_jy_imports_tipper

                        is_method, infos = _pydev_jy_imports_tipper.ismethod(obj)
                        ret = ''
                        if is_method:
                            for info in infos:
                                ret += info.get_as_doc()
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


    def do_exec_code(self, code, is_single_line):
        try:
            code_fragment = CodeFragment(code, is_single_line)
            more = self.need_more(code_fragment)
            if not more:
                code_fragment = self.buffer
                self.buffer = None
                self.exec_queue.put(code_fragment)

            return more
        except:
            traceback.print_exc()
            return False

    def execLine(self, line):
        return self.do_exec_code(line, True)


    def execMultipleLines(self, lines):
        if IS_JYTHON:
            for line in lines.split('\n'):
                self.do_exec_code(line, True)
        else:
            return self.do_exec_code(lines, False)


    def interrupt(self):
        self.buffer = None # Also clear the buffer when it's interrupted.
        try:
            if self.interruptable:
                called = False
                try:
                    # Fix for #PyDev-500: Console interrupt can't interrupt on sleep
                    import os
                    import signal
                    if os.name == 'posix':
                        # On Linux we can't interrupt 0 as in Windows because it's
                        # actually owned by a process -- on the good side, signals
                        # work much better on Linux!
                        os.kill(os.getpid(), signal.SIGINT)
                        called = True

                    elif os.name == 'nt':
                        # Stupid windows: sending a Ctrl+C to a process given its pid
                        # is absurdly difficult.
                        # There are utilities to make it work such as
                        # http://www.latenighthacking.com/projects/2003/sendSignal/
                        # but fortunately for us, it seems Python does allow a CTRL_C_EVENT
                        # for the current process in Windows if pid 0 is passed... if we needed
                        # to send a signal to another process the approach would be
                        # much more difficult.
                        # Still, note that CTRL_C_EVENT is only Python 2.7 onwards...
                        # Also, this doesn't seem to be documented anywhere!? (stumbled
                        # upon it by chance after digging quite a lot).
                        os.kill(0, signal.CTRL_C_EVENT)
                        called = True
                except:
                    # Many things to go wrong (from CTRL_C_EVENT not being there
                    # to failing import signal)... if that's the case, ask for
                    # forgiveness and go on to the approach which will interrupt
                    # the main thread (but it'll only work when it's executing some Python
                    # code -- not on sleep() for instance).
                    pass

                if not called:
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

    def start_exec(self):
        self.interruptable = True

    def get_server(self):
        if getattr(self, 'host', None) is not None:
            return xmlrpclib.Server('http://%s:%s' % (self.host, self.client_port))
        else:
            return None

    server = property(get_server)

    def finish_exec(self, more):
        self.interruptable = False

        server = self.get_server()

        if server is not None:
            return server.NotifyFinished(more)
        else:
            return True

    def getFrame(self):
        xml = "<xml>"
        xml += pydevd_xml.frame_vars_to_xml(self.get_namespace())
        xml += "</xml>"

        return xml

    def getVariable(self, attributes):
        xml = "<xml>"
        valDict = pydevd_vars.resolve_var(self.get_namespace(), attributes)
        if valDict is None:
            valDict = {}

        keys = valDict.keys()

        for k in keys:
            xml += pydevd_vars.var_to_xml(valDict[k], to_string(k))

        xml += "</xml>"

        return xml

    def getArray(self, attr, roffset, coffset, rows, cols, format):
        xml = "<xml>"
        name = attr.split("\t")[-1]
        array = pydevd_vars.eval_in_context(name, self.get_namespace(), self.get_namespace())

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
        result = pydevd_vars.eval_in_context(expression, self.get_namespace(), self.get_namespace())

        xml += pydevd_vars.var_to_xml(result, expression)

        xml += "</xml>"

        return xml

    def changeVariable(self, attr, value):
        def do_change_variable():
            Exec('%s=%s' % (attr, value), self.get_namespace(), self.get_namespace())

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
            f.f_locals = self.get_namespace()
            return f
        else:
            return self.orig_find_frame(thread_id, frame_id)

    def connectToDebugger(self, debuggerPort):
        '''
        Used to show console with variables connection.
        Mainly, monkey-patches things in the debugger structure so that the debugger protocol works.
        '''
        def do_connect_to_debugger():
            try:
                # Try to import the packages needed to attach the debugger
                import pydevd
                from _pydev_imps import _pydev_threading as threading

            except:
                # This happens on Jython embedded in host eclipse
                traceback.print_exc()
                sys.stderr.write('pydevd is not available, cannot connect\n',)

            from _pydev_bundle import pydev_localhost
            threading.currentThread().__pydevd_id__ = "console_main"

            self.orig_find_frame = pydevd_vars.find_frame
            pydevd_vars.find_frame = self._findFrame

            self.debugger = pydevd.PyDB()
            try:
                self.debugger.connect(pydev_localhost.get_localhost(), debuggerPort)
                self.debugger.prepare_to_run()
                from _pydevd_bundle import pydevd_tracing
                pydevd_tracing.SetTrace(None)
            except:
                traceback.print_exc()
                sys.stderr.write('Failed to connect to target debugger.\n')

            # Register to process commands when idle
            self.debugrunning = False
            try:
                import pydevconsole
                pydevconsole.set_debug_hook(self.debugger.process_internal_commands)
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
            from _pydev_bundle.pydev_versioncheck import versionok_for_gui
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