from contextlib import contextmanager

from _pydevd_bundle.pydevd_constants import get_frame
from _pydev_imps._pydev_saved_modules import thread, threading

try:
    import cStringIO as StringIO #may not always be available @UnusedImport
except:
    try:
        import StringIO #@Reimport
    except:
        import io as StringIO


import sys #@Reimport
import traceback

_original_settrace = sys.settrace

class TracingFunctionHolder:
    '''This class exists just to keep some variables (so that we don't keep them in the global namespace). 
    '''
    _original_tracing = None
    _warn = True
    _lock = thread.allocate_lock()
    _traceback_limit = 1
    _warnings_shown = {}
 
 
def get_exception_traceback_str():
    exc_info = sys.exc_info()
    s = StringIO.StringIO()
    traceback.print_exception(exc_info[0], exc_info[1], exc_info[2], file=s)
    return s.getvalue()

def _get_stack_str(frame):
    
    msg = '\nIf this is needed, please check: ' + \
          '\nhttp://pydev.blogspot.com/2007/06/why-cant-pydev-debugger-work-with.html' + \
          '\nto see how to restore the debug tracing back correctly.\n' 
          
    if TracingFunctionHolder._traceback_limit:
        s = StringIO.StringIO()
        s.write('Call Location:\n')
        traceback.print_stack(f=frame, limit=TracingFunctionHolder._traceback_limit, file=s)
        msg = msg + s.getvalue()
    
    return msg

def _internal_set_trace(tracing_func):
    if TracingFunctionHolder._warn:
        frame = get_frame()
        if frame is not None and frame.f_back is not None:
            if not frame.f_back.f_code.co_filename.lower().endswith('threading.py'):
            
                message = \
                '\nPYDEV DEBUGGER WARNING:' + \
                '\nsys.settrace() should not be used when the debugger is being used.' + \
                '\nThis may cause the debugger to stop working correctly.' + \
                '%s' % _get_stack_str(frame.f_back)
                
                if message not in TracingFunctionHolder._warnings_shown:
                    #only warn about each message once...
                    TracingFunctionHolder._warnings_shown[message] = 1
                    sys.stderr.write('%s\n' % (message,))
                    sys.stderr.flush()

    if TracingFunctionHolder._original_tracing:
        TracingFunctionHolder._original_tracing(tracing_func)


def SetTrace(tracing_func, frame_eval_func=None, dummy_tracing_func=None):
    if tracing_func is not None and frame_eval_func is not None:
        # There is no need to set tracing function if frame evaluation is available
        frame_eval_func()
        tracing_func = dummy_tracing_func

    if TracingFunctionHolder._original_tracing is None:
        #This may happen before replace_sys_set_trace_func is called.
        sys.settrace(tracing_func)
        return

    current_thread = threading.currentThread()
    do_not_trace_before = getattr(current_thread, 'pydev_do_not_trace', None)
    if do_not_trace_before:
        return
    try:
        TracingFunctionHolder._lock.acquire()
        current_thread.pydev_do_not_trace = True  # avoid settrace reentering
        TracingFunctionHolder._warn = False
        _internal_set_trace(tracing_func)
        TracingFunctionHolder._warn = True
    finally:
        TracingFunctionHolder._lock.release()
        current_thread.pydev_do_not_trace = do_not_trace_before


def replace_sys_set_trace_func():
    if TracingFunctionHolder._original_tracing is None:
        TracingFunctionHolder._original_tracing = sys.settrace
        sys.settrace = _internal_set_trace

def restore_sys_set_trace_func():
    if TracingFunctionHolder._original_tracing is not None:
        sys.settrace = TracingFunctionHolder._original_tracing
        TracingFunctionHolder._original_tracing = None

def settrace_while_running_if_frame_eval(py_db, trace_func):
    if not py_db.ready_to_run:
        # do it if only debug session is started
        return

    if py_db.frame_eval_func is None:
        return

    threads = threading.enumerate()
    try:
        for t in threads:
            if getattr(t, 'is_pydev_daemon_thread', False):
                continue
            additional_info = None
            try:
                additional_info = t.additional_info
            except AttributeError:
                pass  # that's ok, no info currently set
            if additional_info is None:
                continue

            for frame in additional_info.iter_frames(t):
                py_db.set_trace_for_frame_and_parents(frame, overwrite_prev_trace=True, dispatch_func=trace_func)
            py_db.enable_cache_frames_without_breaks(False)
            # sometimes (when script enters new frames too fast), we can't enable tracing only in the appropriate
            # frame. So, if breakpoint was added during run, we should disable frame evaluation forever.
            py_db.do_not_use_frame_eval = True
    except:
        traceback.print_exc()
