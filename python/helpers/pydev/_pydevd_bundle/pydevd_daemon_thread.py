#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import sys
import traceback
import weakref

import pydevd_tracing
from _pydev_bundle import pydev_log
from _pydev_imps._pydev_saved_modules import threading
from _pydevd_bundle.pydevd_constants import USE_LOW_IMPACT_MONITORING
if USE_LOW_IMPACT_MONITORING:
    from _pydevd_bundle.pydevd_pep_669_tracing_wrapper import disable_pep669_monitoring


class PyDBDaemonThread(threading.Thread):
    def __init__(self, py_db=None, target_and_args=None):
        """
        :param target_and_args:
            tuple(func, args, kwargs) if this should be a function and args to run.
            -- Note: use through run_as_pydevd_daemon_thread().
        """
        threading.Thread.__init__(self)
        self._py_db = weakref.ref(py_db) if py_db is not None else None
        self._kill_received = False
        mark_as_pydevd_daemon_thread(self)
        self._target_and_args = target_and_args

    @property
    def py_db(self):
        return self._py_db() if self._py_db is not None else None

    def run(self):
        created_pydb_daemon = self.py_db.created_pydb_daemon_threads if self.py_db is not None else {}
        created_pydb_daemon[self] = 1
        try:
            try:
                self._stop_trace()
                self._warn_pydevd_thread_is_traced()
                self._on_run()
            except:
                if sys is not None and traceback is not None:
                    traceback.print_exc()
        finally:
            del created_pydb_daemon[self]

    def _on_run(self):
        if self._target_and_args is not None:
            target, args, kwargs = self._target_and_args
            target(*args, **kwargs)
        else:
            raise NotImplementedError('Should be reimplemented by: %s' % self.__class__)

    def do_kill_pydev_thread(self):
        if not self._kill_received:
            pydev_log.debug("%s received kill signal" % self.name)
            self._kill_received = True

    def _stop_trace(self):
        if self.pydev_do_not_trace:
            if USE_LOW_IMPACT_MONITORING:
                disable_pep669_monitoring(all_threads=False)
                return
            pydevd_tracing.SetTrace(None)  # no debugging on this thread

    def _warn_pydevd_thread_is_traced(self):
        if self.pydev_do_not_trace and sys.gettrace():
            pydev_log.debug("The debugger thread '%s' is traced which may lead to debugging performance issues." % self.__class__.__name__)


def mark_as_pydevd_daemon_thread(thread):
    thread.pydev_do_not_trace = True
    thread.is_pydev_daemon_thread = True
    thread.daemon = True


def run_as_pydevd_daemon_thread(py_db, func, *args, **kwargs):
    """
    Runs a function as a pydevd daemon thread (without any tracing in place).
    """
    t = PyDBDaemonThread(py_db, target_and_args=(func, args, kwargs))
    t.name = '%s (pydevd daemon thread)' % (func.__name__,)
    t.start()
    return t