from _pydevd_bundle import pydevd_thrift
from _pydevd_bundle.pydevd_comm import PyDBDaemonThread
from _pydevd_bundle.pydevd_comm import threading
from _pydevd_bundle.pydevd_comm import time
from _pydevd_bundle.pydevd_constants import ASYNC_EVAL_TIMEOUT_SEC


class ThriftAbstractGetValueAsyncThread(PyDBDaemonThread):
    """
    Abstract class for a thread, which evaluates values for async variables
    """
    def __init__(self, server, seq, var_objects, user_type_renderers=None):
        PyDBDaemonThread.__init__(self)
        self.server = server
        self.seq = seq
        self.var_objs = var_objects
        self.cancel_event = threading.Event()
        self.user_type_renderers = user_type_renderers

    def send_result(self, xml):
        raise NotImplementedError()

    def _on_run(self):
        start = time.time()
        values = []
        for (var_obj, name) in self.var_objs:
            current_time = time.time()
            if current_time - start > ASYNC_EVAL_TIMEOUT_SEC or self.cancel_event.is_set():
                break
            # pydev_console_thrift.DebugValue()
            values.append(pydevd_thrift.var_to_struct(var_obj, name, evaluate_full_value=True, user_type_renderers=self.user_type_renderers))
        self.send_result(values)


class ThriftGetValueAsyncThreadConsole(ThriftAbstractGetValueAsyncThread):
    """
    A thread for evaluation async values, which returns result for Console
    Send result directly to Console's server
    """
    def send_result(self, values):
        if self.server is not None:
            self.server.returnFullValue(self.seq, values)
