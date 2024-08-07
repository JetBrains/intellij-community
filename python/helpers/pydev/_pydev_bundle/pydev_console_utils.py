import os
import sys
import traceback

from _pydev_bundle import _pydev_imports_tipper
from _pydev_bundle.pydev_code_executor import BaseCodeExecutor
from _pydev_bundle.pydev_console_types import CodeFragment
from _pydev_bundle.pydev_imports import Exec
from _pydev_bundle.pydev_stdin import StdIn, DebugConsoleStdIn
from _pydevd_bundle import pydevd_thrift
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_comm import InternalDataViewerAction
from _pydevd_bundle.pydevd_constants import IS_JYTHON, dict_iter_items
from _pydevd_bundle.pydevd_tables import exec_table_command
from _pydevd_bundle.pydevd_user_type_renderers import parse_set_type_renderers_message
from pydev_console.pydev_protocol import CompletionOption, CompletionOptionType, \
    PythonUnhandledException, PythonTableException

try:
    import cStringIO as StringIO  # may not always be available @UnusedImport
except:
    try:
        import StringIO  # @Reimport
    except:
        import io as StringIO

# translation to Thrift `CompletionOptionType` enumeration
COMPLETION_OPTION_TYPES = {
    _pydev_imports_tipper.TYPE_IMPORT: CompletionOptionType.IMPORT,
    _pydev_imports_tipper.TYPE_CLASS: CompletionOptionType.CLASS,
    _pydev_imports_tipper.TYPE_FUNCTION: CompletionOptionType.FUNCTION,
    _pydev_imports_tipper.TYPE_ATTR: CompletionOptionType.ATTR,
    _pydev_imports_tipper.TYPE_BUILTIN: CompletionOptionType.BUILTIN,
    _pydev_imports_tipper.TYPE_PARAM: CompletionOptionType.PARAM,
    _pydev_imports_tipper.TYPE_IPYTHON: CompletionOptionType.IPYTHON,
    _pydev_imports_tipper.TYPE_IPYTHON_MAGIC: CompletionOptionType.IPYTHON_MAGIC,
}


def _to_completion_option(word):
    name, documentation, args, ret_type = word
    completion_option_type = COMPLETION_OPTION_TYPES[ret_type]
    return CompletionOption(name, documentation, args.split(), completion_option_type)


# =======================================================================================================================
# Null
# =======================================================================================================================
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


# =======================================================================================================================
# BaseInterpreterInterface
# =======================================================================================================================
class BaseInterpreterInterface(BaseCodeExecutor):
    def __init__(self, mainThread, connect_status_queue=None, rpc_client=None):
        super(BaseInterpreterInterface, self).__init__()

        self.mainThread = mainThread
        self.banner_shown = False
        self.connect_status_queue = connect_status_queue
        self.user_type_renderers = {}

        self.rpc_client = rpc_client
        self._first_command_executed = False

    def build_banner(self):
        return 'print({0})\n'.format(repr(self.get_greeting_msg()))

    def create_std_in(self, debugger=None, original_std_in=None):
        if debugger is None:
            return StdIn(self, self.rpc_client, original_stdin=original_std_in)
        else:
            return DebugConsoleStdIn(dbg=debugger, original_stdin=original_std_in)

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
        try:
            if not self.banner_shown:
                line = self.build_banner() + line
                self.banner_shown = True
            return self.do_exec_code(line, True)
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def execMultipleLines(self, lines):
        try:
            if not self.banner_shown:
                lines = self.build_banner() + lines
                self.banner_shown = True
            if IS_JYTHON:
                for line in lines.split('\n'):
                    self.do_exec_code(line, True)
            else:
                return self.do_exec_code(lines, False)
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def close(self):
        sys.exit(0)

    def get_server(self):
        if getattr(self, 'rpc_client', None) is not None:
            return self.rpc_client
        else:
            return None

    server = property(get_server)

    def ShowConsole(self):
        server = self.get_server()
        if server is not None:
            server.showConsole()

    def notify_first_command_executed(self):
        pass

    def finish_exec(self, more, exception_occurred):
        self.interruptable = False

        if not self._first_command_executed:
            self.notify_first_command_executed()
            self._first_command_executed = True

        server = self.get_server()

        if server is not None:
            return server.notifyFinished(more, exception_occurred)
        else:
            return True

    def getFrame(self, group_type):
        try:
            hidden_ns = self.get_ipython_hidden_vars_dict()
            return pydevd_thrift.frame_vars_to_struct(self.get_namespace(), group_type, hidden_ns, self.user_type_renderers)
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def getVariable(self, attributes):
        try:
            namespace = self.get_namespace()
            debug_values = []
            val_dict = pydevd_vars.resolve_compound_var_object_fields(namespace, attributes, self.user_type_renderers)
            if val_dict is None:
                val_dict = {}

            keys = val_dict.keys()
            for k in keys:
                val = val_dict[k]
                evaluate_full_value = pydevd_thrift.should_evaluate_full_value(val)
                debug_values.append(pydevd_thrift.var_to_struct(val, k, evaluate_full_value=evaluate_full_value, user_type_renderers=self.user_type_renderers))

            return debug_values
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def getArray(self, attr, roffset, coffset, rows, cols, format):
        try:
            name = attr.split("\t")[-1]
            array = pydevd_vars.eval_in_context(name, self.get_namespace(), self.get_namespace())
            return pydevd_thrift.table_like_struct_to_thrift_struct(array, name, roffset, coffset, rows, cols, format)
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def setUserTypeRenderers(self, message):
        try:
            renderers = parse_set_type_renderers_message(message)
            self.user_type_renderers = renderers
            return True
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def execDataViewerAction(self, varName, action, args):
        try:
            tmp_var = pydevd_vars.eval_in_context(varName, self.get_namespace(), self.get_namespace())
            return InternalDataViewerAction.act(tmp_var, action, args.split("\t"))

        except Exception as e:
            raise PythonUnhandledException(type(e).__name__ + "\n" + traceback.format_exc())

    def evaluate(self, expression, do_trunc):
        # returns `DebugValue` of evaluated expression
        try:
            namespace = self.get_namespace()
            result = pydevd_vars.eval_in_context(expression, namespace, namespace)
            return [pydevd_thrift.var_to_struct(result, expression, do_trim=do_trunc, user_type_renderers=self.user_type_renderers)]
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def do_get_completions(self, text, act_tok):
        """Retrieves completion options.

        Returns the array with completion options tuples.

        :param text: the full text of the expression to complete
        :param act_tok: resolved part of the expression
        :return: the array of tuples `(name, documentation, args, ret_type)`

        :Example:

            Let us execute ``import time`` line in the Python console. Then try
            to complete ``time.sle`` expression. At this point the method would
            receive ``time.sle`` as ``text`` parameter and ``time.`` as
            ``act_tok`` parameter. The result would contain the array with the
            following tuple among others: ``[..., ('sleep',
            'sleep(seconds)\\n\\nDelay execution ...', '(seconds)', '2'),
            ...]``.
        """
        try:
            from _pydev_bundle._pydev_completer import Completer

            completer = Completer(self.get_namespace(), None)
            return completer.complete(act_tok)
        except:
            traceback.print_exc()
            return []

    def getCompletions(self, text, act_tok):
        try:
            words = self.do_get_completions(text, act_tok)
            return [_to_completion_option(word) for word in words]
        except:
            raise PythonUnhandledException(traceback.format_exc())

    def loadFullValue(self, seq, scope_attrs):
        """
        Evaluate full value for async Console variables in a separate thread and send results to IDE side
        :param seq: id of command
        :param scope_attrs: a sequence of variables with their attributes separated by NEXT_VALUE_SEPARATOR
        (i.e.: obj\tattr1\tattr2NEXT_VALUE_SEPARATORobj2\attr1\tattr2)
        :return:
        """
        try:
            frame_variables = self.get_namespace()
            var_objects = []
            # vars = scope_attrs.split(NEXT_VALUE_SEPARATOR)
            vars = scope_attrs
            for var_attrs in vars:
                if '\t' in var_attrs:
                    name, attrs = var_attrs.split('\t', 1)

                else:
                    name = var_attrs
                    attrs = None
                if name in frame_variables.keys():
                    var_object = pydevd_vars.resolve_var_object(frame_variables[name], attrs)
                else:
                    var_object = pydevd_vars.eval_in_context(name, frame_variables, frame_variables)

                var_objects.append((var_object, name))

            from _pydev_bundle.pydev_console_commands import ThriftGetValueAsyncThreadConsole
            t = ThriftGetValueAsyncThreadConsole(self.get_server(), seq, var_objects, self.user_type_renderers)
            t.start()
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def changeVariable(self, attr, value):
        try:
            def do_change_variable():
                Exec('%s=%s' % (attr, value), self.get_namespace(), self.get_namespace())

            # Important: it has to be really enabled in the main thread, so, schedule
            # it to run in the main thread.
            self.exec_queue.put(do_change_variable)
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    def _findFrame(self, thread_id, frame_id):
        '''
        Used to show console with variables connection.
        Always return a frame where the locals map to our internal namespace.
        '''
        VIRTUAL_FRAME_ID = "1"  # matches PyStackFrameConsole.java
        VIRTUAL_CONSOLE_ID = "console_main"  # matches PyThreadConsole.java
        if thread_id == VIRTUAL_CONSOLE_ID and frame_id == VIRTUAL_FRAME_ID:
            f = FakeFrame()
            f.f_globals = {}  # As globals=locals here, let's simply let it empty (and save a bit of network traffic).
            f.f_locals = self.get_namespace()
            return f
        else:
            return self.orig_find_frame(thread_id, frame_id)

    def connectToDebugger(self, debuggerPort, debugger_host=None, debugger_options=None, extra_envs=None):
        '''
        Used to show console with variables connection.
        Mainly, monkey-patches things in the debugger structure so that the debugger protocol works.
        '''
        try:
            if debugger_options is None:
                debugger_options = {}

            for (env_name, value) in dict_iter_items(extra_envs):
                existing_value = os.environ.get(env_name, None)
                if existing_value:
                    os.environ[env_name] = "%s%c%s" % (existing_value, os.path.pathsep, value)
                else:
                    os.environ[env_name] = value
                if env_name == "PYTHONPATH":
                    sys.path.append(value)

            def do_connect_to_debugger():
                try:
                    # Try to import the packages needed to attach the debugger
                    import pydevd
                    from _pydev_imps._pydev_saved_modules import threading

                except:
                    # This happens on Jython embedded in host eclipse
                    traceback.print_exc()
                    sys.stderr.write('pydevd is not available, cannot connect\n', )

                from _pydevd_bundle.pydevd_constants import set_thread_id
                from _pydev_bundle import pydev_localhost
                set_thread_id(threading.current_thread(), "console_main")

                self.orig_find_frame = pydevd_vars.find_frame
                pydevd_vars.find_frame = self._findFrame

                self.debugger = pydevd.PyDB()
                try:
                    pydevd.apply_debugger_options(debugger_options)
                    if debugger_host is None or pydev_localhost.is_localhost(debugger_host):
                        host = pydev_localhost.get_localhost()
                    else:
                        host = debugger_host
                    self.debugger.connect(host, debuggerPort)
                    self.debugger.prepare_to_run()
                    self.debugger.disable_tracing()
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
                    sys.stderr.write(
                        'Version of Python does not support debuggable Interactive Console.\n')

            # Important: it has to be really enabled in the main thread, so, schedule
            # it to run in the main thread.
            self.exec_queue.put(do_connect_to_debugger)
            return ('connect complete',)
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())

    #
    def execTableCommand(self, command, command_type, start_index, end_index, format):
        try:
            try:
                start_index = int(start_index)
                end_index = int(end_index)
            except ValueError:
                start_index = None
                end_index = None
            success, res = exec_table_command(command, command_type,
                                              start_index, end_index, format,
                                              self.get_namespace(),
                                              self.get_namespace())
            if success:
                return res
        except:
            traceback.print_exc()
            raise PythonUnhandledException(traceback.format_exc())
        if not success:
            raise PythonTableException(str(res))

    def handshake(self):
        if self.connect_status_queue is not None:
            self.connect_status_queue.put(True)
        return "PyCharm"

    def get_connect_status_queue(self):
        return self.connect_status_queue

    def hello(self, input_str):
        # Don't care what the input string is
        return ("Hello eclipse",)


# =======================================================================================================================
# FakeFrame
# =======================================================================================================================
class FakeFrame:
    '''
    Used to show console with variables connection.
    A class to be used as a mock of a frame.
    '''
