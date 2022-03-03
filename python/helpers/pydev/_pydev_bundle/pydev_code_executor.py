import os
import signal
import sys
import traceback

from _pydev_bundle._pydev_calltip_util import get_description
from _pydev_bundle.pydev_imports import _queue
from _pydev_bundle.pydev_stdin import DebugConsoleStdIn
from _pydevd_bundle import pydevd_vars


# =======================================================================================================================
# BaseCodeExecutor
# =======================================================================================================================
class BaseCodeExecutor(object):
    def __init__(self):
        self.interruptable = False
        self.exec_queue = _queue.Queue(0)
        self.buffer = None
        self.mpl_modules_for_patching = {}
        self.init_mpl_modules_for_patching()

    def get_greeting_msg(self):
        return 'PyDev console: starting.\n'

    def init_mpl_modules_for_patching(self):
        from pydev_ipython.matplotlibtools import activate_matplotlib, activate_pylab, activate_pyplot
        self.mpl_modules_for_patching = {
            "matplotlib": lambda: activate_matplotlib(self.enableGui),
            "matplotlib.pyplot": activate_pyplot,
            "pylab": activate_pylab
        }

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

    def create_std_in(self, debugger=None, original_std_in=None):
        return DebugConsoleStdIn(dbg=debugger, original_stdin=original_std_in)

    def add_exec(self, code_fragment, debugger=None):
        original_in = sys.stdin
        try:
            help = None
            if 'pydoc' in sys.modules:
                pydoc = sys.modules['pydoc']  # Don't import it if it still is not there.

                if hasattr(pydoc, 'help'):
                    # You never know how will the API be changed, so, let's code defensively here
                    help = pydoc.help
                    if not hasattr(help, 'input'):
                        help = None
        except:
            # Just ignore any error here
            pass

        more = False
        exception_occurred = False
        try:
            sys.stdin = self.create_std_in(debugger, original_in)
            try:
                if help is not None:
                    # This will enable the help() function to work.
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
                        import pydevd_tracing
                        pydevd_tracing.SetTrace(self.debugger.trace_dispatch)

                    more, exception_occurred = self.do_add_exec(code_fragment)

                    if hasattr(self, 'debugger'):
                        import pydevd_tracing
                        pydevd_tracing.SetTrace(None)

                    self.finish_exec(more, exception_occurred)
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

        return more, exception_occurred

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

    def __resolve_reference__(self, text):
        """

        :type text: str
        """
        obj = None
        if '.' not in text:
            try:
                obj = self.get_namespace()[text]
            except KeyError:
                pass

            if obj is None:
                try:
                    obj = self.get_namespace()['__builtins__'][text]
                except:
                    pass

            if obj is None:
                try:
                    obj = getattr(self.get_namespace()['__builtins__'], text, None)
                except:
                    pass

        else:
            try:
                last_dot = text.rindex('.')
                parent_context = text[0:last_dot]
                res = pydevd_vars.eval_in_context(parent_context, self.get_namespace(), self.get_namespace())
                obj = getattr(res, text[last_dot + 1:])
            except:
                pass
        return obj

    def getDescription(self, text):
        try:
            obj = self.__resolve_reference__(text)
            if obj is None:
                return ''
            return get_description(obj)
        except:
            return ''

    def start_exec(self):
        self.interruptable = True

    def finish_exec(self, more, exception_occurred):
        self.interruptable = False
        return True

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

    def get_ipython_hidden_vars_dict(self):
        return None

    def interrupt(self):
        self.buffer = None  # Also clear the buffer when it's interrupted.
        try:
            if self.interruptable:
                called = False
                try:
                    # Fix for #PyDev-500: Console interrupt can't interrupt on sleep
                    if os.name == 'posix':
                        # On Linux we can't interrupt 0 as in Windows because it's
                        # actually owned by a process -- on the good side, signals
                        # work much better on Linux!
                        os.kill(os.getpid(), signal.SIGINT)
                        called = True

                    elif os.name == 'nt':
                        # In windows sending a Ctrl+C to a process given its pid is difficult.
                        # There are utilities to make it work such as
                        # http://www.latenighthacking.com/projects/2003/sendSignal/
                        # but fortunately for us, it seems Python does allow a CTRL_C_EVENT
                        # for the current process in Windows if pid 0 is passed... if we needed
                        # to send a signal to another process the approach would be
                        # much more difficult.
                        # Still, note that CTRL_C_EVENT is only Python 2.7 onwards...
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
                    if hasattr(thread, 'interrupt_main'):  # Jython doesn't have it
                        thread.interrupt_main()
                    else:
                        self.mainThread._thread.interrupt()  # Jython
            self.finish_exec(False, False)
            return True
        except:
            traceback.print_exc()
            return False
