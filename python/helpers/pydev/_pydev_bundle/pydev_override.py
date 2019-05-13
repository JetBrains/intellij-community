def overrides(method):
    '''
    Initially meant to be used as
    
    class B:
        @overrides(A.m1)
        def m1(self):
            pass
            
    but as we want to be compatible with Jython 2.1 where decorators have an uglier syntax (needing an assign
    after the method), it should now be used without being a decorator as below (in which case we don't even check
    for anything, just that the parent name was actually properly loaded).
    
    i.e.:
    
    class B:
        overrides(A.m1)
        def m1(self):
            pass
    '''
    return

#    def wrapper(func):
#        if func.__name__ != method.__name__:
#            msg = "Wrong @override: %r expected, but overwriting %r."
#            msg = msg % (func.__name__, method.__name__)
#            raise AssertionError(msg)
#
#        if func.__doc__ is None:
#            func.__doc__ = method.__doc__
#
#        return func
#
#    return wrapper

def implements(method):
    return
#    def wrapper(func):
#        if func.__name__ != method.__name__:
#            msg = "Wrong @implements: %r expected, but implementing %r."
#            msg = msg % (func.__name__, method.__name__)
#            raise AssertionError(msg)
#
#        if func.__doc__ is None:
#            func.__doc__ = method.__doc__
#
#        return func
#
#    return wrapper