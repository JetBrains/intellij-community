# TODO that would make IPython integration better
# - show output other times then when enter was pressed
# - support proper exit to allow IPython to cleanup (e.g. temp files created with %edit)
# - support Ctrl-D (Ctrl-Z on Windows)
# - use IPython (numbered) prompts in PyDev
# - better integration of IPython and PyDev completions
# - some of the semantics on handling the code completion are not correct:
#   eg: Start a line with % and then type c should give %cd as a completion by it doesn't
#       however type %c and request completions and %cd is given as an option
#   eg: Completing a magic when user typed it without the leading % causes the % to be inserted
#       to the left of what should be the first colon.
"""Interface to TerminalInteractiveShell for PyDev Interactive Console frontend
   for IPython 0.11 to 1.0+.
"""

from __future__ import print_function

import os
import sys
import codeop
import traceback
from copy import deepcopy

from IPython.core.error import UsageError
from IPython.core.interactiveshell import InteractiveShell, InteractiveShellABC
from IPython.core.usage import default_banner_parts
from IPython.core.formatters import DisplayFormatter
from IPython.core import release

from IPython.terminal.interactiveshell import TerminalInteractiveShell
from IPython.terminal.ipapp import TerminalIPythonApp
from IPython import InteractiveShell

from traitlets import Type
from traitlets import CBool, Unicode


from _pydevd_bundle.pydevd_constants import dict_keys, dict_iter_items
from _pydevd_bundle.pydevd_ipython_console_output import PyDevDebugDisplayHook, PyDevDebugDisplayPub
from _pydev_bundle.pydev_ipython_rich_output import PyDevDisplayHook, PyDevDisplayPub, \
    patch_stdout
from _pydev_bundle.pydev_ipython_completer import init_shell_completer

default_pydev_banner_parts = default_banner_parts
default_pydev_banner = ''.join(default_pydev_banner_parts)

INLINE_OUTPUT_SUPPORTED = os.getenv('INLINE_OUTPUT_SUPPORTED', False)


def show_in_pager(self, strng, *args, **kwargs):
    """ Run a string through pager """
    # On PyDev we just output the string, there are scroll bars in the console
    # to handle "paging". This is the same behaviour as when TERM==dump (see
    # page.py)
    # for compatibility with mime-bundle form:
    if isinstance(strng, dict):
        strng = strng['text/plain']
    print(strng)


def create_editor_hook(rpc_client):
    def call_editor(filename, line=0, wait=True):
        """ Open an editor in PyDev """
        if line is None:
            line = 0

        # Make sure to send an absolution path because unlike most editor hooks
        # we don't launch a process. This is more like what happens in the zmqshell
        filename = os.path.abspath(filename)

        # import sys
        # sys.__stderr__.write('Calling editor at: %s:%s\n' % (pydev_host, pydev_client_port))

        # Tell PyDev to open the editor
        rpc_client.IPythonEditor(filename, str(line))

        if wait:
            try:
                raw_input("Press Enter when done editing:")
            except NameError:
                input("Press Enter when done editing:")
    return call_editor


class PyDevTerminalInteractiveShell(TerminalInteractiveShell):
    banner1 = Unicode(default_pydev_banner, config=True,
        help="""The part of the banner to be printed before the profile"""
    )

    # TODO term_title: (can PyDev's title be changed???, see terminal.py for where to inject code, in particular set_term_title as used by %cd)
    # for now, just disable term_title
    term_title = CBool(False)

    # Note in version 0.11 there is no guard in the IPython code about displaying a
    # warning, so with 0.11 you get:
    #  WARNING: Readline services not available or not loaded.
    #  WARNING: The auto-indent feature requires the readline library
    # Disable readline, readline type code is all handled by PyDev (on Java side)
    readline_use = CBool(False)
    # autoindent has no meaning in PyDev (PyDev always handles that on the Java side),
    # and attempting to enable it will print a warning in the absence of readline.
    autoindent = CBool(False)
    # Force console to not give warning about color scheme choice and default to NoColor.
    # TODO It would be nice to enable colors in PyDev but:
    # - The PyDev Console (Eclipse Console) does not support the full range of colors, so the
    #   effect isn't as nice anyway at the command line
    # - If done, the color scheme should default to LightBG, but actually be dependent on
    #   any settings the user has (such as if a dark theme is in use, then Linux is probably
    #   a better theme).
    colors_force = CBool(True)
    colors = Unicode("NoColor")
    # Since IPython 5 the terminal interface is not compatible with Emacs `inferior-shell` and
    # the `simple_prompt` flag is needed
    simple_prompt = CBool(True)
    pydev_curr_exec_line = 0
    
    if INLINE_OUTPUT_SUPPORTED:
        displayhook_class = Type(PyDevDisplayHook)
        display_pub_class = Type(PyDevDisplayPub)

    def __init__(self, *args, **kwargs):
        super(PyDevTerminalInteractiveShell, self).__init__(*args, **kwargs)
        if INLINE_OUTPUT_SUPPORTED:
            try:
                self.enable_matplotlib('inline')
            except:
                sys.stderr.write("Failed to enable inline matplotlib plots\n")
                sys.stderr.flush()

    def patch_stdout_if_needed(self):
        if INLINE_OUTPUT_SUPPORTED:
            patch_stdout(self)

    # In the PyDev Console, GUI control is done via hookable XML-RPC server
    @staticmethod
    def enable_gui(gui=None, app=None):
        """Switch amongst GUI input hooks by name.
        """
        # Deferred import
        if gui != 'inline':
            from pydev_ipython.inputhook import enable_gui as real_enable_gui
            try:
                return real_enable_gui(gui, app)
            except ValueError as e:
                raise UsageError("%s" % e)

    def init_display_formatter(self):
        if INLINE_OUTPUT_SUPPORTED:
            self.display_formatter = DisplayFormatter(parent=self)
            self.configurables.append(self.display_formatter)
            self.display_formatter.ipython_display_formatter.enabled = True
        else:
            super(PyDevTerminalInteractiveShell, self).init_display_formatter()

    #-------------------------------------------------------------------------
    # Things related to hooks
    #-------------------------------------------------------------------------

    def init_hooks(self):
        super(PyDevTerminalInteractiveShell, self).init_hooks()
        self.set_hook('show_in_pager', show_in_pager)

    #-------------------------------------------------------------------------
    # Things related to exceptions
    #-------------------------------------------------------------------------

    def showtraceback(self, exc_tuple=None, *args, **kwargs):
        # IPython does a lot of clever stuff with Exceptions. However mostly
        # it is related to IPython running in a terminal instead of an IDE.
        # (e.g. it prints out snippets of code around the stack trace)
        # PyDev does a lot of clever stuff too, so leave exception handling
        # with default print_exc that PyDev can parse and do its clever stuff
        # with (e.g. it puts links back to the original source code)
        try:
            if exc_tuple is None:
                etype, value, tb = sys.exc_info()
            else:
                etype, value, tb = exc_tuple
        except ValueError:
            return

        if tb is not None:
            traceback.print_exception(etype, value, tb)
            sys.last_type, sys.last_value, sys.last_traceback = etype, value, tb

    def init_completer(self):
        init_shell_completer(self)

    #-------------------------------------------------------------------------
    # Things related to aliases
    #-------------------------------------------------------------------------

    def init_alias(self):
        # InteractiveShell defines alias's we want, but TerminalInteractiveShell defines
        # ones we don't. So don't use super and instead go right to InteractiveShell
        InteractiveShell.init_alias(self)

    #-------------------------------------------------------------------------
    # Things related to exiting
    #-------------------------------------------------------------------------
    def ask_exit(self):
        """ Ask the shell to exit. Can be overiden and used as a callback. """
        # TODO PyDev's console does not have support from the Python side to exit
        # the console. If user forces the exit (with sys.exit()) then the console
        # simply reports errors. e.g.:
        # >>> import sys
        # >>> sys.exit()
        # Failed to create input stream: Connection refused
        # >>>
        # Console already exited with value: 0 while waiting for an answer.
        # Error stream:
        # Output stream:
        # >>>
        #
        # Alternatively if you use the non-IPython shell this is what happens
        # >>> exit()
        # <type 'exceptions.SystemExit'>:None
        # >>>
        # <type 'exceptions.SystemExit'>:None
        # >>>
        #
        super(PyDevTerminalInteractiveShell, self).ask_exit()
        print('To exit the PyDev Console, terminate the console within IDE.')

    #-------------------------------------------------------------------------
    # Things related to magics
    #-------------------------------------------------------------------------

    def init_magics(self):
        super(PyDevTerminalInteractiveShell, self).init_magics()
        # TODO Any additional magics for PyDev?


class PyDebuggerTerminalInteractiveShell(PyDevTerminalInteractiveShell):
    """
    InteractiveShell for the Jupyter Debug Console.
    Print result outputs to the shell.
    See method 'do_add_exec' from  'pydev/_pydev_bundle/pydev_ipython_code_executor.py'.
    """

    displayhook_class = Type(PyDevDebugDisplayHook)
    display_pub_class = Type(PyDevDebugDisplayPub)
    new_instance = None


InteractiveShellABC.register(PyDevTerminalInteractiveShell)  # @UndefinedVariable
InteractiveShellABC.register(PyDebuggerTerminalInteractiveShell)

class PyDevIpythonApp(TerminalIPythonApp):
    def initialize(self, shell_cls):
        """Do actions after construct, but before starting the app."""
        cl_config = deepcopy(self.config)
        self.init_profile_dir()
        self.init_config_files()
        self.load_config_file()
        self.update_config(cl_config)
        self.init_path()
        self.init_shell(shell_cls)
        self.init_extensions()
        self.init_code()

    def init_shell(self, shell_cls):
        self.shell = shell_cls.instance()
        self.shell.configurables.append(self)


#=======================================================================================================================
# _PyDevIPythonFrontEnd
#=======================================================================================================================
class _PyDevIPythonFrontEnd:

    version = release.__version__

    def __init__(self, is_jupyter_debugger=False):
        # Create and initialize our IPython instance.
        self.is_jupyter_debugger = is_jupyter_debugger
        if is_jupyter_debugger:
            if self._has_shell_instance(PyDebuggerTerminalInteractiveShell, 'new_instance'):
                self.ipython = PyDebuggerTerminalInteractiveShell.new_instance
            else:
                # if we already have some InteractiveConsole instance (Python Console: Attach Debugger)
                if self._has_shell_instance(PyDevTerminalInteractiveShell, '_instance'):
                    PyDevTerminalInteractiveShell.clear_instance()

                InteractiveShell.clear_instance()

                self.ipython = self._init_ipy_app(PyDebuggerTerminalInteractiveShell).shell
                PyDebuggerTerminalInteractiveShell.new_instance = PyDebuggerTerminalInteractiveShell._instance
        else:
            if self._has_shell_instance(PyDevTerminalInteractiveShell, '_instance'):
                self.ipython = PyDevTerminalInteractiveShell._instance
            else:
                self.ipython = self._init_ipy_app(PyDevTerminalInteractiveShell).shell

        self._curr_exec_line = 0
        self._curr_exec_lines = []

    def _init_ipy_app(self, shell_cls):
        application = PyDevIpythonApp()
        application.initialize(shell_cls)
        return application


    def _has_shell_instance(self, shell_cls, instance_str):
        return getattr(shell_cls, instance_str, None) is not None


    def update(self, globals, locals):
        ns = self.ipython.user_ns

        for key in dict_keys(self.ipython.user_ns):
            if key not in locals:
                locals[key] = ns[key]

        self.ipython.user_global_ns.clear()
        self.ipython.user_global_ns.update(globals)

        # If `globals` and `locals` passed to the method are the same objects, we have to ensure that they are also
        # the same in the IPython evaluation context to avoid troubles with some corner-cases such as generator expressions.
        # See: `pydevd_console_integration.console_exec()`.
        self.ipython.user_ns = self.ipython.user_global_ns if globals is locals else locals

        if hasattr(self.ipython, 'history_manager') and getattr(self.ipython.history_manager, 'save_thread', None) is not None:
            self.ipython.history_manager.save_thread.pydev_do_not_trace = True  # don't trace ipython history saving thread

    def complete(self, string):
        try:
            if string:
                return self.ipython.complete(None, line=string, cursor_pos=string.__len__())
            else:
                return self.ipython.complete(string, string, 0)
        except:
            # Silence completer exceptions
            pass

    def is_complete(self, string):
        #Based on IPython 0.10.1

        if string in ('', '\n'):
            # Prefiltering, eg through ipython0, may return an empty
            # string although some operations have been accomplished. We
            # thus want to consider an empty string as a complete
            # statement.
            return True
        else:
            try:
                # Add line returns here, to make sure that the statement is
                # complete (except if '\' was used).
                # This should probably be done in a different place (like
                # maybe 'prefilter_input' method? For now, this works.
                clean_string = string.rstrip('\n')
                if not clean_string.endswith('\\'):
                    clean_string += '\n\n'

                is_complete = codeop.compile_command(
                    clean_string,
                    "<string>",
                    "exec"
                )
            except Exception:
                # XXX: Hack: return True so that the
                # code gets executed and the error captured.
                is_complete = True
            return is_complete

    def getCompletions(self, text, act_tok):
        # Get completions from IPython and from PyDev and merge the results
        # IPython only gives context free list of completions, while PyDev
        # gives detailed information about completions.
        try:
            TYPE_IPYTHON = '11'
            TYPE_IPYTHON_MAGIC = '12'
            _line, ipython_completions = self.complete(text)

            from _pydev_bundle._pydev_completer import Completer
            completer = Completer(self.get_namespace(), None)
            ret = completer.complete(act_tok)
            append = ret.append
            ip = self.ipython
            pydev_completions = set([f[0] for f in ret])
            for ipython_completion in ipython_completions:

                #PyCharm was not expecting completions with '%'...
                #Could be fixed in the backend, but it's probably better
                #fixing it at PyCharm.
                #if ipython_completion.startswith('%'):
                #    ipython_completion = ipython_completion[1:]

                if ipython_completion not in pydev_completions:
                    pydev_completions.add(ipython_completion)
                    inf = ip.object_inspect(ipython_completion)
                    if inf['type_name'] == 'Magic function':
                        pydev_type = TYPE_IPYTHON_MAGIC
                    else:
                        pydev_type = TYPE_IPYTHON
                    pydev_doc = inf['docstring']
                    if pydev_doc is None:
                        pydev_doc = ''
                    append((ipython_completion, pydev_doc, '', pydev_type))
            return ret
        except:
            import traceback;traceback.print_exc()
            return []

    def get_namespace(self):
        return self.ipython.user_ns

    def clear_buffer(self):
        del self._curr_exec_lines[:]

    def add_exec(self, line):
        if self._curr_exec_lines:
            self._curr_exec_lines.append(line)

            buf = '\n'.join(self._curr_exec_lines)

            if self.is_complete(buf):
                self._curr_exec_line += 1
                self.ipython.pydev_curr_exec_line = self._curr_exec_line
                res = self.ipython.run_cell(buf)
                del self._curr_exec_lines[:]
                if res.error_in_exec is not None:
                    return False, True
                else:
                    return False, False #execute complete (no more)

            return True, False #needs more
        else:

            if not self.is_complete(line):
                #Did not execute
                self._curr_exec_lines.append(line)
                return True, False #needs more
            else:
                self._curr_exec_line += 1
                self.ipython.pydev_curr_exec_line = self._curr_exec_line
                if not self.is_jupyter_debugger:
                    res = self.ipython.run_cell(line, store_history=True)
                else:
                    res = self.ipython.run_cell(line, store_history=False)
                if res.error_in_exec is not None:
                    return False, True
                else:
                    return False, False #execute complete (no more)

    def is_automagic(self):
        return self.ipython.automagic

    def get_greeting_msg(self):
        return 'PyDev console: using IPython %s\n' % self.version


class _PyDevFrontEndContainer:
    _instance = None
    _last_rpc_client = None

class _PyDebuggerFrontEndContainer:
    _instance = None

def get_client():
    return _PyDevFrontEndContainer._last_rpc_client


def get_pydev_ipython_frontend(rpc_client, is_jupyter_debugger=False):
    if is_jupyter_debugger:
        if _PyDebuggerFrontEndContainer._instance is None:
            _PyDebuggerFrontEndContainer._instance = _PyDevIPythonFrontEnd(is_jupyter_debugger)

        return _PyDebuggerFrontEndContainer._instance

    if _PyDevFrontEndContainer._instance is None:
        _PyDevFrontEndContainer._instance = _PyDevIPythonFrontEnd(is_jupyter_debugger)

    if _PyDevFrontEndContainer._last_rpc_client != rpc_client:
        _PyDevFrontEndContainer._last_rpc_client = rpc_client

        # Back channel to PyDev to open editors (in the future other
        # info may go back this way. This is the same channel that is
        # used to get stdin, see StdIn in pydev_console_utils)
        _PyDevFrontEndContainer._instance.ipython.hooks['editor'] = create_editor_hook(rpc_client)

        # Note: setting the callback directly because setting it with set_hook would actually create a chain instead
        # of ovewriting at each new call).
        # _PyDevFrontEndContainer._instance.ipython.set_hook('editor', create_editor_hook(pydev_host, pydev_client_port))

    return _PyDevFrontEndContainer._instance


def get_ipython_hidden_vars(ipython_shell):
    try:
        if hasattr(ipython_shell, 'user_ns_hidden'):
            user_ns_hidden = ipython_shell.user_ns_hidden
            if isinstance(user_ns_hidden, dict):
                # Since IPython 2 dict `user_ns_hidden` contains hidden variables and values
                user_hidden_dict = user_ns_hidden.copy()
            else:
                # In IPython 1.x `user_ns_hidden` used to be a set with names of hidden variables
                user_hidden_dict = dict([(key, val) for key, val in dict_iter_items(ipython_shell.user_ns)
                                         if key in user_ns_hidden])

            # while `_`, `__` and `___` were not initialized, they are not presented in `user_ns_hidden`
            user_hidden_dict.setdefault('_', '')
            user_hidden_dict.setdefault('__', '')
            user_hidden_dict.setdefault('___', '')

            return user_hidden_dict
    except:
        # Getting IPython variables shouldn't break loading frame variables
        traceback.print_exc()
