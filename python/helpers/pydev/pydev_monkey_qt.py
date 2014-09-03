from __future__ import nested_scopes

def set_trace_in_qt():
    import pydevd_tracing
    from pydevd_comm import GetGlobalDebugger
    debugger = GetGlobalDebugger()
    if debugger is not None:
        pydevd_tracing.SetTrace(debugger.trace_dispatch)
        
        
_patched_qt = False
def patch_qt():
    '''
    This method patches qt (PySide, PyQt4, PyQt5) so that we have hooks to set the tracing for QThread.
    '''
    
    # Avoid patching more than once
    global _patched_qt
    if _patched_qt:
        return
    
    _patched_qt = True
    
    try:
        from PySide import QtCore
    except:
        try:
            from PyQt4 import QtCore
        except:
            try:
                from PyQt5 import QtCore
            except:
                return
    
    _original_thread_init = QtCore.QThread.__init__
    _original_runnable_init = QtCore.QRunnable.__init__
    
    
    class FuncWrapper:
        
        def __init__(self, original):
            self._original = original
        
        def __call__(self, *args, **kwargs):
            set_trace_in_qt()
            return self._original(*args, **kwargs)
    
    class StartedSignalWrapper:  # Wrapper for the QThread.started signal
        
        def __init__(self, thread, original_started):
            self.thread = thread
            self.original_started = original_started
            
        def connect(self, func, *args, **kwargs):
            return self.original_started.connect(FuncWrapper(func), *args, **kwargs)
        
        def disconnect(self, *args, **kwargs):
            return self.original_started.disconnect(*args, **kwargs)
        
        def emit(self, *args, **kwargs):
            return self.original_started.emit(*args, **kwargs)
            
    
    class ThreadWrapper(QtCore.QThread):  # Wrapper for QThread
        
        def __init__(self, *args, **kwargs):
            _original_thread_init(self)
    
            self._original_run = self.run
            self.run = self._new_run
            self._original_started = self.started
            self.started = StartedSignalWrapper(self, self.started)
            
        def _new_run(self):
            set_trace_in_qt()
            return self._original_run()
    
    class RunnableWrapper(QtCore.QRunnable):  # Wrapper for QRunnable
        
        def __init__(self, *args, **kwargs):
            _original_runnable_init(self)
    
            self._original_run = self.run
            self.run = self._new_run
            
            
        def _new_run(self):
            set_trace_in_qt()
            return self._original_run()
            
    QtCore.QThread = ThreadWrapper
    QtCore.QRunnable = RunnableWrapper
