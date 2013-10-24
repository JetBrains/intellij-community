__author__ = 'ktisha'
try:
  from pkg_resources import EntryPoint
  from setuptools.command import test
  from tcunittest import TeamcityTestRunner
except ImportError:
  raise NameError("Something went wrong, do you have setuptools installed?")

class pycharm_test(test.test):
    def run_tests(self):
        import unittest

        loader_ep = EntryPoint.parse("x=" + self.test_loader)
        loader_class = loader_ep.load(require=False)
        unittest.main(
            None, None, [unittest.__file__] + self.test_args,
            testRunner=TeamcityTestRunner,
            testLoader=loader_class()
        )
