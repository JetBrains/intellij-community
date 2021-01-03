"""
Check that the debugging environment is reset when forking a new process.

In particular it tests that:

- a new instance of the debugger is created in the forked process
- all thread tracing functions are reset on fork so the original debugger instance does not leak through them

.. note:: Meaningful only when frame evaluation is enabled.

"""
import os
import sys

from _pydevd_bundle.pydevd_constants import get_global_debugger
from _pydevd_frame_eval.pydevd_frame_eval_cython_wrapper import get_thread_info_py


def _get_thread_trace_func_debugger():
    thread_info = get_thread_info_py()
    # noinspection PyProtectedMember
    return thread_info.thread_trace_func._args[0]


if __name__ == '__main__':
    original_debugger = get_global_debugger()
    pid = os.fork()  # break here
    if pid == 0:
        debugger_in_fork = get_global_debugger()
        assert original_debugger is not debugger_in_fork, "The debugger in the forked process is a copy of the original debugger."
        # noinspection PyProtectedMember,PyUnresolvedReferences,PyUnresolvedReferences
        frame = sys._getframe()
        assert frame.f_trace is not None, "The trace function expected to be set, but it is `None`."
        assert original_debugger is not _get_thread_trace_func_debugger(), "The thread trace function uses the debugger from the parent " \
                                                                           "process. "
        print('TEST SUCEEDED')
