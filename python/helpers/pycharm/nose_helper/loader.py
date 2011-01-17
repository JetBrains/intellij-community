"""
nose's test loader implements the nosetests functionality
"""

from __future__ import generators

import os
import sys
import unittest
from inspect import isfunction, ismethod
from nose_helper.case import FunctionTestCase, MethodTestCase
from nose_helper.failure import Failure
from nose_helper.config import Config
from nose_helper.selector import defaultSelector
from nose_helper.util import cmp_lineno, func_lineno, isclass, isgenerator, ismethod, isunboundmethod
from nose_helper.util import transplant_class, transplant_func
from nose_helper.suite import ContextSuiteFactory, ContextList

op_normpath = os.path.normpath
op_abspath = os.path.abspath

PYTHON_VERSION_MAJOR = sys.version_info[0]
PYTHON_VERSION_MINOR = sys.version_info[1]

from nose_helper.util import unbound_method
import types

class TestLoader(unittest.TestLoader):
    """Test loader that extends unittest.TestLoader to support nosetests
    """
    config = None
    workingDir = None
    selector = None
    suiteClass = None
    
    def __init__(self):
        """Initialize a test loader.
        """
        self.config = Config()
        self.selector = defaultSelector(self.config)
        self.workingDir = op_normpath(op_abspath(self.config.workingDir))
        self.suiteClass = ContextSuiteFactory(config=self.config)
        unittest.TestLoader.__init__(self)     

    def loadTestsFromGenerator(self, generator, module, lineno):
        """The generator function may yield either:
        * a callable, or
        * a function name resolvable within the same module
        """
        def generate(g=generator, m=module):
            try:
                for test in g():
                    test_func, arg = self.parseGeneratedTest(test)
                    if not hasattr(test_func, '__call__'):
                        test_func = getattr(m, test_func)
                    test_case = FunctionTestCase(test_func, arg=arg, descriptor=g)
                    test_case.lineno = lineno
                    yield test_case
            except KeyboardInterrupt:
                raise
            except:
                exc = sys.exc_info()
                yield Failure(exc[0], exc[1], exc[2])
        return self.suiteClass(generate, context=generator)

    def loadTestsFromModule(self, module, direct = True):
        """Load all tests from module and return a suite containing
        them.
        """
        tests = []
        test_funcs = []
        test_classes = []
        if self.selector.wantModule(module) or direct:
            for item in dir(module):
                test = getattr(module, item, None)
                if isclass(test):
                    if self.selector.wantClass(test):
                        test_classes.append(test)
                elif isfunction(test) and self.selector.wantFunction(test):
                    test_funcs.append(test)
            if PYTHON_VERSION_MAJOR != 3:
                test_classes.sort(lambda a, b: cmp(a.__name__, b.__name__))
                test_funcs.sort(cmp_lineno)
                tests = map(lambda t: self.makeTest(t, parent=module),
                        test_classes + test_funcs)
            else:
                test_classes.sort(key = lambda a: a.__name__)
                test_funcs.sort(key = func_lineno)
                tests = [self.makeTest(t, parent=module) for t in
                                        test_classes + test_funcs]
        return self.suiteClass(ContextList(tests, context=module))


    def loadTestsFromTestClass(self, cls):
        """Load tests from a test class that is *not* a unittest.TestCase
        subclass.
        """
        def wanted(attr, cls=cls, sel=self.selector):
            item = getattr(cls, attr, None)
            if isfunction(item):
                item = unbound_method(cls, item)
            if not ismethod(item):
                return False
            return sel.wantMethod(item)
        cases = [self.makeTest(getattr(cls, case), cls)
                 for case in filter(wanted, dir(cls))]

        return self.suiteClass(ContextList(cases, context=cls))

    def makeTest(self, obj, parent=None):
        try:
            return self._makeTest(obj, parent)
        except (KeyboardInterrupt, SystemExit):
            raise
        except:
            exc = sys.exc_info()
            return Failure(exc[0], exc[1], exc[2])

    def _makeTest(self, obj, parent=None):
        """Given a test object and its parent, return a test case
        or test suite.
        """
        import inspect
        try:
          lineno = inspect.getsourcelines(obj)
        except:
          lineno = ("", 1)
        if isfunction(obj) and parent and not isinstance(parent, types.ModuleType):
          obj = unbound_method(parent, obj)
        if isinstance(obj, unittest.TestCase):
            return obj
        elif isclass(obj):
            if parent and obj.__module__ != parent.__name__:
                obj = transplant_class(obj, parent.__name__)
            if issubclass(obj, unittest.TestCase):
                return self.loadTestsFromTestCase(obj)
            else:
                return self.loadTestsFromTestClass(obj)
        elif ismethod(obj) or isunboundmethod(obj):
            if parent is None:
                parent = obj.__class__
            if issubclass(parent, unittest.TestCase):
                return parent(obj.__name__)
            else:
              if PYTHON_VERSION_MAJOR > 2:
                setattr(obj, "im_class", parent)
                setattr(obj, "im_self", parent)
              test_case = MethodTestCase(obj)
              test_case.lineno = lineno[1]
              return test_case
        elif isfunction(obj):
            setattr(obj, "lineno", lineno[1])
            if hasattr(obj, "__module__"):
                if parent and obj.__module__ != parent.__name__:
                    obj = transplant_func(obj, parent.__name__)
            else:
                if parent:
                    obj = transplant_func(obj, parent.__name__)
                else:
                    obj = transplant_func(obj)

            if isgenerator(obj):
                return self.loadTestsFromGenerator(obj, parent, lineno[1])
            else:
                return FunctionTestCase(obj)
        else:
            return Failure(TypeError,
                           "Can't make a test from %s" % obj)

    def loadTestsFromTestCase(self, testCaseClass):
       """Return a suite of all tests cases contained in testCaseClass"""
       try:
           # PY-2412
           # because of Twisted overrides runTest function and we don't need to harvest them
           import twisted.trial.unittest
           if issubclass(testCaseClass, twisted.trial.unittest.TestCase):
               testCaseNames = self.getTestCaseNames(testCaseClass)
               return self.suiteClass(map(testCaseClass, testCaseNames))
       except ImportError:
           pass

       if issubclass(testCaseClass, unittest.TestSuite):
           raise TypeError("Test cases should not be derived from TestSuite. Maybe you meant to derive from TestCase?")
       testCaseNames = self.getTestCaseNames(testCaseClass)
       if not testCaseNames and hasattr(testCaseClass, 'runTest'):
           testCaseNames = ['runTest']
       return self.suiteClass(map(testCaseClass, testCaseNames))

    def parseGeneratedTest(self, test):
        """Given the yield value of a test generator, return a func and args.
        """
        if not isinstance(test, tuple):         # yield test
            test_func, arg = (test, tuple())
        elif len(test) == 1:                    # yield (test,)
            test_func, arg = (test[0], tuple())
        else:                                   # yield test, foo, bar, ...
            assert len(test) > 1 # sanity check
            test_func, arg = (test[0], test[1:])
        return test_func, arg

defaultLoader = TestLoader()
