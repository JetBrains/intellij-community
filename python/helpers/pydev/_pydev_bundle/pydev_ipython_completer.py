#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import IPython.core.release as IPythonRelease
from IPython.core.completer import IPCompleter
from IPython.utils.strdispatch import StrDispatch


class PyDevIPCompleter(IPCompleter):

    def __init__(self, *args, **kwargs):
        """ Create a Completer that reuses the advanced completion support of PyDev
            in addition to the completion support provided by IPython """
        IPCompleter.__init__(self, *args, **kwargs)
        # Use PyDev for python matches, see getCompletions below
        if self.python_matches in self.matchers:
            # `self.python_matches` matches attributes or global python names
            self.matchers.remove(self.python_matches)


class PyDevIPCompleter6(IPCompleter):
    _pydev_matchers = None

    def __init__(self, *args, **kwargs):
        """ Create a Completer that reuses the advanced completion support of PyDev
            in addition to the completion support provided by IPython """
        IPCompleter.__init__(self, *args, **kwargs)

    @property
    def matchers(self):
        # To remove python_matches we now have to override it as it's now a property in the superclass.
        if self._pydev_matchers is None:
            self._pydev_matchers = self._remove_python_matches(
                IPCompleter.matchers.fget(self))
        return self._pydev_matchers

    @matchers.setter
    def matchers(self, value):
        # Provide a setter for an overridden property
        self._pydev_matchers = self._remove_python_matches(value)

    def _remove_python_matches(self, original_matchers):
        # `self.python_matches` matches attributes or global python names
        if self.python_matches in original_matchers:
            original_matchers.remove(self.python_matches)
        return original_matchers


def init_shell_completer(shell):
    """Initialize the completion machinery.

    This creates a completer that provides the completions that are
    IPython specific. We use this to supplement PyDev's core code
    completions.
    """
    # PyDev uses its own completer and custom hooks so that it uses
    # most completions from PyDev's core completer which provides
    # extra information.
    # See getCompletions for where the two sets of results are merged

    if IPythonRelease._version_major >= 6:
        shell.Completer = _new_completer_600(shell)
    elif IPythonRelease._version_major >= 5:
        shell.Completer = _new_completer_500(shell)
    elif IPythonRelease._version_major >= 2:
        shell.Completer = _new_completer_234(shell)
    elif IPythonRelease._version_major >= 1:
        shell.Completer = _new_completer_100(shell)

    if hasattr(shell.Completer, 'use_jedi'):
        shell.Completer.use_jedi = False

    add_completer_hooks(shell)

    if IPythonRelease._version_major <= 3:
        # Only configure readline if we truly are using readline.  IPython can
        # do tab-completion over the network, in GUIs, etc, where readline
        # itshell may be absent
        if shell.has_readline:
            shell.set_readline_completer()


# -------------------------------------------------------------------------
# Things related to text completion
# -------------------------------------------------------------------------

# The way to construct an IPCompleter changed in most versions,
# so we have a custom, per version implementation of the construction

def _new_completer_100(shell):
    completer = PyDevIPCompleter(shell=shell,
                                 namespace=shell.user_ns,
                                 global_namespace=shell.user_global_ns,
                                 alias_table=shell.alias_manager.alias_table,
                                 use_readline=shell.has_readline,
                                 parent=shell,
                                 )
    return completer


def _new_completer_234(shell):
    # correct for IPython versions 2.x, 3.x, 4.x
    completer = PyDevIPCompleter(shell=shell,
                                 namespace=shell.user_ns,
                                 global_namespace=shell.user_global_ns,
                                 use_readline=shell.has_readline,
                                 parent=shell,
                                 )
    return completer


def _new_completer_500(shell):
    completer = PyDevIPCompleter(shell=shell,
                                 namespace=shell.user_ns,
                                 global_namespace=shell.user_global_ns,
                                 use_readline=False,
                                 parent=shell
                                 )
    return completer


def _new_completer_600(shell):
    completer = PyDevIPCompleter6(shell=shell,
                                  namespace=shell.user_ns,
                                  global_namespace=shell.user_global_ns,
                                  use_readline=False,
                                  parent=shell
                                  )
    return completer


def add_completer_hooks(shell):
    from IPython.core.completerlib import module_completer, magic_run_completer, \
        cd_completer
    try:
        from IPython.core.completerlib import reset_completer
    except ImportError:
        # reset_completer was added for rel-0.13
        reset_completer = None
    shell.configurables.append(shell.Completer)

    # Add custom completers to the basic ones built into IPCompleter
    sdisp = shell.strdispatchers.get('complete_command', StrDispatch())
    shell.strdispatchers['complete_command'] = sdisp
    shell.Completer.custom_completers = sdisp

    shell.set_hook('complete_command', module_completer, str_key='import')
    shell.set_hook('complete_command', module_completer, str_key='from')
    shell.set_hook('complete_command', magic_run_completer, str_key='%run')
    shell.set_hook('complete_command', cd_completer, str_key='%cd')
    if reset_completer:
        shell.set_hook('complete_command', reset_completer, str_key='%reset')
