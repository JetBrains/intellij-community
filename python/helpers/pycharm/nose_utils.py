import sys
import traceback
import datetime
import unittest

from tcmessages import TeamcityServiceMessages
from tcunittest import strclass
from tcunittest import TeamcityTestResult

_ERROR_TEST_NAME = "ERROR"

try:
  from nose.failure import Failure
  from nose.util import isclass # backwards compat
  from nose.config import Config
  from nose.result import TextTestResult
  from nose import SkipTest
  from nose.plugins.errorclass import ErrorClassPlugin
except (Exception, ):
  e = sys.exc_info()[1]
  raise NameError(
          "Something went wrong, do you have nosetest installed? I got this error: %s" % e)

class TeamcityPlugin(ErrorClassPlugin, TextTestResult, TeamcityTestResult):
  """
  TeamcityTest plugin for nose tests
  """
  name = "TeamcityPlugin"
  enabled = True

  def __init__(self, stream=sys.stderr, descriptions=None, verbosity=1,
               config=None, errorClasses=None):
    super(TeamcityPlugin, self).__init__()

    if errorClasses is None:
      errorClasses = {}

    self.errorClasses = errorClasses
    if config is None:
      config = Config()
    self.config = config
    self.output = stream
    self.messages = TeamcityServiceMessages(self.output,
                                            prepend_linebreak=True)
    self.messages.testMatrixEntered()
    self.current_suite = None
    TextTestResult.__init__(self, stream, descriptions, verbosity, config,
                            errorClasses)
    TeamcityTestResult.__init__(self, stream)

  def configure(self, options, conf):
    if not self.can_configure:
      return
    self.conf = conf


  def _is_failure(self, test):
    try:
      return isinstance(test.test, Failure)
    except AttributeError:
      return False

  def addError(self, test, err):
    exctype, value, tb = err
    err = self.formatErr(err)
    if self._is_failure(test):
        self.messages.testError(_ERROR_TEST_NAME, message='Error', details=err, duration=self.__getDuration(test))
        return

    if exctype == SkipTest:
      self.messages.testIgnored(self.getTestName(test), message='Skip')
    else:
      self.messages.testError(self.getTestName(test), message='Error', details=err, duration=self.__getDuration(test))

  def formatErr(self, err):
    exctype, value, tb = err
    if isinstance(value, str):
      try:
        value = exctype(value)
      except TypeError:
        pass
    return ''.join(traceback.format_exception(exctype, value, tb))

  def is_gen(self, test):
    if hasattr(test, "test") and hasattr(test.test, "descriptor"):
      if test.test.descriptor is not None:
        return True
    return False


  def getTestName(self, test):
    if hasattr(test, "error_context"):
      return test.error_context
    test_name_full = str(test)
    if self.is_gen(test):
      return test_name_full

    ind_1 = test_name_full.rfind('(')
    if ind_1 != -1:
      return test_name_full[:ind_1]
    return test_name_full


  def addFailure(self, test, err):
    err = self.formatErr(err)

    self.messages.testFailed(self.getTestName(test),
                             message='Failure', details=err)


  def addSkip(self, test, reason):
    self.messages.testIgnored(self.getTestName(test), message=reason)


  def _getSuite(self, test):
    if hasattr(test, "suite"):
      suite = strclass(test.suite)
      suite_location = test.suite.location
      location = test.suite.abs_location
      if hasattr(test, "lineno"):
        location = location + ":" + str(test.lineno)
      else:
        location = location + ":" + str(test.test.lineno)
    else:
      suite = strclass(test.__class__)
      suite_location = "python_nosetestid://" + suite
      try:
        from nose.util import func_lineno

        if hasattr(test.test, "descriptor") and test.test.descriptor:
          suite_location = "file://" + self.test_address(
                  test.test.descriptor)
          location = suite_location + ":" + str(
                  func_lineno(test.test.descriptor))
        else:
          suite_location = "file://" + self.test_address(
                  test.test.test)
          location = "file://" + self.test_address(
                  test.test.test) + ":" + str(func_lineno(test.test.test))
      except:
        test_id = test.id()
        suite_id = test_id[:test_id.rfind(".")]
        suite_location = "python_nosetestid://" + str(suite_id)
        location = "python_nosetestid://" + str(test_id)
    return (location, suite_location)


  def test_address(self, test):
    if hasattr(test, "address"):
      return test.address()[0]
    t = type(test)
    file = None
    import types, os

    if (t == types.FunctionType or issubclass(t, type) or t == type
        or isclass(test)):
      module = getattr(test, '__module__', None)
      if module is not None:
        m = sys.modules[module]
        file = getattr(m, '__file__', None)
        if file is not None:
          file = os.path.abspath(file)
      if file.endswith("pyc"):
        file = file[:-1]
      return file
    raise TypeError("I don't know what %s is (%s)" % (test, t))


  def getSuiteName(self, test):
    test_name_full = str(test)

    ind_1 = test_name_full.rfind('(')
    if self.is_gen(test) and ind_1 != -1:
      ind = test_name_full[:ind_1].rfind('.')
      if ind != -1:
        return test_name_full[:ind]

    if ind_1 != -1:
      return test_name_full[ind_1 + 1: -1]
    ind = test_name_full.rfind('.')
    if ind != -1:
      return test_name_full[:test_name_full.rfind(".")]
    return test_name_full


  def startTest(self, test):
    location, suite_location = self._getSuite(test)
    if self._is_failure(test):
      self.messages.testStarted(_ERROR_TEST_NAME, location=suite_location)
      return
    suite = self.getSuiteName(test)
    if suite != self.current_suite:
      if self.current_suite:
        self.messages.testSuiteFinished(self.current_suite)
      self.current_suite = suite
      self.messages.testSuiteStarted(self.current_suite,
                                     location=suite_location)
    setattr(test, "startTime", datetime.datetime.now())
    self.messages.testStarted(self.getTestName(test), location=location)


  def stopTest(self, test):
    duration = self.__getDuration(test)
    if self._is_failure(test):
      return  # Finish reported by testError
    self.messages.testFinished(self.getTestName(test),
                               duration=int(duration))

  def __getDuration(self, test):
    start = getattr(test, "startTime", datetime.datetime.now())
    d = datetime.datetime.now() - start
    duration = d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
    return duration

  def finalize(self, result):
    if self.current_suite:
      self.messages.testSuiteFinished(self.current_suite)
      self.current_suite = None


class TeamcityNoseRunner(unittest.TextTestRunner):
  """Test runner that supports teamcity output
  """

  def __init__(self, stream=sys.stdout, descriptions=1, verbosity=1,
               config=None):
    if config is None:
      config = Config()
    self.config = config

    unittest.TextTestRunner.__init__(self, stream, descriptions, verbosity)


  def _makeResult(self):
    return TeamcityPlugin(self.stream,
                          self.descriptions,
                          self.verbosity,
                          self.config)

  def run(self, test):
    """Overrides to provide plugin hooks and defer all output to
    the test result class.
    """
    #for 2.5 compat
    plugins = self.config.plugins
    plugins.configure(self.config.options, self.config)
    plugins.begin()
    wrapper = plugins.prepareTest(test)
    if wrapper is not None:
      test = wrapper

    # plugins can decorate or capture the output stream
    wrapped = self.config.plugins.setOutputStream(self.stream)
    if wrapped is not None:
      self.stream = wrapped

    result = self._makeResult()
    test(result)
    result.endLastSuite()
    plugins.finalize(result)

    return result