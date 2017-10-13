'''For debug purpose we are replacing actual builtin property by the debug property
'''
from _pydevd_bundle.pydevd_comm import get_global_debugger
from _pydevd_bundle.pydevd_constants import DebugInfoHolder, IS_PY2
from _pydevd_bundle import pydevd_tracing

#=======================================================================================================================
# replace_builtin_property
#=======================================================================================================================
def replace_builtin_property(new_property=None):
    if new_property is None:
        new_property = DebugProperty
    original = property
    if IS_PY2:
        try:
            import __builtin__
            __builtin__.__dict__['property'] = new_property
        except:
            if DebugInfoHolder.DEBUG_TRACE_LEVEL:
                import traceback;traceback.print_exc() #@Reimport
    else:
        try:
            import builtins #Python 3.0 does not have the __builtin__ module @UnresolvedImport
            builtins.__dict__['property'] = new_property
        except:
            if DebugInfoHolder.DEBUG_TRACE_LEVEL:
                import traceback;traceback.print_exc() #@Reimport
    return original


#=======================================================================================================================
# DebugProperty
#=======================================================================================================================
class DebugProperty(object):
    """A custom property which allows python property to get
    controlled by the debugger and selectively disable/re-enable
    the tracing.
    """


    def __init__(self, fget=None, fset=None, fdel=None, doc=None):
        self.fget = fget
        self.fset = fset
        self.fdel = fdel
        self.__doc__ = doc


    def __get__(self, obj, objtype=None):
        if obj is None:
            return self
        global_debugger = get_global_debugger()
        try:
            if global_debugger is not None and global_debugger.disable_property_getter_trace:
                pydevd_tracing.SetTrace(None)
            if self.fget is None:
                raise AttributeError("unreadable attribute")
            return self.fget(obj)
        finally:
            if global_debugger is not None:
                pydevd_tracing.SetTrace(global_debugger.trace_dispatch)


    def __set__(self, obj, value):
        global_debugger = get_global_debugger()
        try:
            if global_debugger is not None and global_debugger.disable_property_setter_trace:
                pydevd_tracing.SetTrace(None)
            if self.fset is None:
                raise AttributeError("can't set attribute")
            self.fset(obj, value)
        finally:
            if global_debugger is not None:
                pydevd_tracing.SetTrace(global_debugger.trace_dispatch)


    def __delete__(self, obj):
        global_debugger = get_global_debugger()
        try:
            if global_debugger is not None and global_debugger.disable_property_deleter_trace:
                pydevd_tracing.SetTrace(None)
            if self.fdel is None:
                raise AttributeError("can't delete attribute")
            self.fdel(obj)
        finally:
            if global_debugger is not None:
                pydevd_tracing.SetTrace(global_debugger.trace_dispatch)


    def getter(self, fget):
        """Overriding getter decorator for the property
        """
        self.fget = fget
        return self


    def setter(self, fset):
        """Overriding setter decorator for the property
        """
        self.fset = fset
        return self


    def deleter(self, fdel):
        """Overriding deleter decorator for the property
        """
        self.fdel = fdel
        return self

