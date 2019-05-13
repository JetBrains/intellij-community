"""
Test Suites
"""
from __future__ import generators

import sys
import unittest
from nose_helper.case import Test
from nose_helper.config import Config
from nose_helper.util import isclass, resolve_name, try_run
PYTHON_VERSION_MAJOR = sys.version_info[0]
class LazySuite(unittest.TestSuite):
    """A suite that may use a generator as its list of tests
    """
    def __init__(self, tests=()):
        self._set_tests(tests)
                
    def __iter__(self):
        return iter(self._tests)

    def __hash__(self):
        return object.__hash__(self)

    def addTest(self, test):
        self._precache.append(test)

    def __nonzero__(self):
        if self._precache:
            return True
        if self.test_generator is None:
            return False
        try:
            test = self.test_generator.next()
            if test is not None:
                self._precache.append(test)
                return True
        except StopIteration:
            pass
        return False

    def _get_tests(self):
        if self.test_generator is not None:
            for i in self.test_generator:
                yield i
        for test in self._precache:
            yield test

    def _set_tests(self, tests):
        self._precache = []
        is_suite = isinstance(tests, unittest.TestSuite)
        if hasattr(tests, '__call__') and not is_suite:
            self.test_generator = tests()
            self.test_generator_counter = list(tests())
        elif is_suite:
            self.addTests([tests])
            self.test_generator = None
            self.test_generator_counter = None
        else:
            self.addTests(tests)
            self.test_generator = None
            self.test_generator_counter = None

    def countTestCases(self):
        counter = 0
        generator = self.test_generator_counter
        if generator is not None:
            for test in generator:
                counter +=1
        for test in self._precache:
            counter += test.countTestCases()
        return counter

    _tests = property(_get_tests, _set_tests, None,
                      "Access the tests in this suite.")

class ContextSuite(LazySuite):
    """A suite with context.
    """
    was_setup = False
    was_torndown = False
    classSetup = ('setup_class', 'setup_all', 'setupClass', 'setupAll',
                     'setUpClass', 'setUpAll')
    classTeardown = ('teardown_class', 'teardown_all', 'teardownClass',
                     'teardownAll', 'tearDownClass', 'tearDownAll')
    moduleSetup = ('setup_module', 'setupModule', 'setUpModule', 'setup',
                   'setUp')
    moduleTeardown = ('teardown_module', 'teardownModule', 'tearDownModule',
                      'teardown', 'tearDown')
    packageSetup = ('setup_package', 'setupPackage', 'setUpPackage')
    packageTeardown = ('teardown_package', 'teardownPackage',
                       'tearDownPackage')

    def __init__(self, tests=(), context=None, factory=None,
                 config=None):
        
        self.context = context
        self.factory = factory
        if config is None:
            config = Config()
        self.config = config
        self.has_run = False
        self.error_context = None
        LazySuite.__init__(self, tests)

    def __hash__(self):
        return object.__hash__(self)

    def __call__(self, *arg, **kw):
        return self.run(*arg, **kw)

    def _exc_info(self):
        return sys.exc_info()

    def addTests(self, tests, context=None):
        if context:
            self.context = context
        if PYTHON_VERSION_MAJOR < 3 and isinstance(tests, basestring):
          raise TypeError("tests must be an iterable of tests, not a string")
        else:
          if isinstance(tests, str):
            raise TypeError("tests must be an iterable of tests, not a string")
        for test in tests:
            self.addTest(test)

    def run(self, result):
        """Run tests in suite inside of suite fixtures.
        """
        result, orig = result, result
        try:
            self.setUp()
        except KeyboardInterrupt:
            raise
        except:
            self.error_context = 'setup'
            result.addError(self, self._exc_info())
            return
        try:
            for test in self._tests:
                if result.shouldStop:
                    break
                test(orig)
        finally:
            self.has_run = True
            try:
                self.tearDown()
            except KeyboardInterrupt:
                raise
            except:
                self.error_context = 'teardown'
                result.addError(self, self._exc_info())

    def setUp(self):
        if not self:
           return
        if self.was_setup:
            return
        context = self.context
        if context is None:
            return

        factory = self.factory
        if factory:
            ancestors = factory.context.get(self, [])[:]
            while ancestors:
                ancestor = ancestors.pop()
                if ancestor in factory.was_setup:
                    continue
                self.setupContext(ancestor)
            if not context in factory.was_setup:
                self.setupContext(context)
        else:
            self.setupContext(context)
        self.was_setup = True

    def setupContext(self, context):
        if self.factory:
            if context in self.factory.was_setup:
                return
            self.factory.was_setup[context] = self
        if isclass(context):
            names = self.classSetup
        else:
            names = self.moduleSetup
            if hasattr(context, '__path__'):
                names = self.packageSetup + names
        try_run(context, names)

    def tearDown(self):
        if not self.was_setup or self.was_torndown:
            return
        self.was_torndown = True
        context = self.context
        if context is None:
            return

        factory = self.factory
        if factory:
            ancestors = factory.context.get(self, []) + [context]
            for ancestor in ancestors:
                if not ancestor in factory.was_setup:
                    continue
                if ancestor in factory.was_torndown:
                    continue
                setup = factory.was_setup[ancestor]
                if setup is self:
                    self.teardownContext(ancestor)
        else:
            self.teardownContext(context)
        
    def teardownContext(self, context):
        if self.factory:
            if context in self.factory.was_torndown:
                return
            self.factory.was_torndown[context] = self
        if isclass(context):
            names = self.classTeardown
        else:
            names = self.moduleTeardown
            if hasattr(context, '__path__'):
                names = self.packageTeardown + names
        try_run(context, names)

    def _get_wrapped_tests(self):
        for test in self._get_tests():
            if isinstance(test, Test) or isinstance(test, unittest.TestSuite):
                yield test
            else:
                yield Test(test,
                           config=self.config)

    _tests = property(_get_wrapped_tests, LazySuite._set_tests, None,
                      "Access the tests in this suite. Tests are returned "
                      "inside of a context wrapper.")

class ContextSuiteFactory(object):
    suiteClass = ContextSuite
    def __init__(self, config=None):
        if config is None:
            config = Config()
        self.config = config
        self.suites = {}
        self.context = {}
        self.was_setup = {}
        self.was_torndown = {}

    def __call__(self, tests, **kw):
        """Return 'ContextSuite' for tests.
        """
        context = kw.pop('context', getattr(tests, 'context', None))
        if context is None:
            tests = self.wrapTests(tests)
            context = self.findContext(tests)
        return self.makeSuite(tests, context, **kw)
        
    def ancestry(self, context):
        """Return the ancestry of the context
        """
        if context is None:
            return
        if hasattr(context, 'im_class'):
            context = context.im_class
        if hasattr(context, '__module__'):
            ancestors = context.__module__.split('.')
        elif hasattr(context, '__name__'):
            ancestors = context.__name__.split('.')[:-1]
        else:
            raise TypeError("%s has no ancestors?" % context)
        while ancestors:
            yield resolve_name('.'.join(ancestors))
            ancestors.pop()

    def findContext(self, tests):
        if hasattr(tests, '__call__') or isinstance(tests, unittest.TestSuite):
            return None
        context = None
        for test in tests:
            # Don't look at suites for contexts, only tests
            ctx = getattr(test, 'context', None)
            if ctx is None:
                continue
            if context is None:
                context = ctx
        return context

    def makeSuite(self, tests, context, **kw):
        suite = self.suiteClass(
            tests, context=context, config=self.config, factory=self, **kw)
        if context is not None:
            self.suites.setdefault(context, []).append(suite)
            self.context.setdefault(suite, []).append(context)
            for ancestor in self.ancestry(context):
                self.suites.setdefault(ancestor, []).append(suite)
                self.context[suite].append(ancestor)

        return suite

    def wrapTests(self, tests):
        if hasattr(tests, '__call__') or isinstance(tests, unittest.TestSuite):
            return tests
        wrapped = []
        for test in tests:
            if isinstance(test, Test) or isinstance(test, unittest.TestSuite):
                wrapped.append(test)
            elif isinstance(test, ContextList):
                wrapped.append(self.makeSuite(test, context=test.context))
            else:
                wrapped.append(
                    Test(test, config=self.config)
                    )
        return wrapped

class ContextList(object):
    """a group of tests in a context.
    """
    def __init__(self, tests, context=None):
      self.tests = tests
      self.context = context

    def __iter__(self):
        return iter(self.tests)
