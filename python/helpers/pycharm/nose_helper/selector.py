"""
Test Selection
"""
import unittest
from nose_helper.config import Config

class Selector(object):
    """Examines test candidates and determines whether,
    given the specified configuration, the test candidate should be selected
    as a test.
    """
    def __init__(self, config):
        if config is None:
            config = Config()
        self.configure(config)

    def configure(self, config):
        self.config = config
        self.match = config.testMatch
        
    def matches(self, name):
        return self.match.search(name)
    
    def wantClass(self, cls):
        """Is the class a wanted test class
        """
        declared = getattr(cls, '__test__', None)
        if declared is not None:
            wanted = declared
        else:
            wanted = (not cls.__name__.startswith('_')
                      and (issubclass(cls, unittest.TestCase)
                           or self.matches(cls.__name__)))
        
        return wanted

    def wantFunction(self, function):
        """Is the function a test function
        """
        try:
            if hasattr(function, 'compat_func_name'):
                funcname = function.compat_func_name
            else:
                funcname = function.__name__
        except AttributeError:
            # not a function
            return False
        import inspect
        arguments = inspect.getargspec(function)
        if len(arguments[0]) or arguments[1] or arguments[2]:
            return False
        declared = getattr(function, '__test__', None)
        if declared is not None:
            wanted = declared
        else:
            wanted = not funcname.startswith('_') and self.matches(funcname)

        return wanted

    def wantMethod(self, method):
        """Is the method a test method
        """
        try:
            method_name = method.__name__
        except AttributeError:
            # not a method
            return False
        if method_name.startswith('_'):
            # never collect 'private' methods
            return False
        declared = getattr(method, '__test__', None)
        if declared is not None:
            wanted = declared
        else:
            wanted = self.matches(method_name)
        return wanted
    
    def wantModule(self, module):
        """Is the module a test module
        we always want __main__.
        """
        declared = getattr(module, '__test__', None)
        if declared is not None:
            wanted = declared
        else:
            wanted = self.matches(module.__name__.split('.')[-1]) \
                     or module.__name__ == '__main__'
        return wanted
        
defaultSelector = Selector        

