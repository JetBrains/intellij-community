import os
import traceback, sys
from unittest import TestResult
import datetime

from tcmessages import TeamcityServiceMessages

PYTHON_VERSION_MAJOR = sys.version_info[0]


def strclass(cls):
  if not cls.__name__:
    return cls.__module__
  return "%s.%s" % (cls.__module__, cls.__name__)


def smart_str(s):
  encoding = 'utf-8'
  errors = 'strict'
  if PYTHON_VERSION_MAJOR < 3:
    is_string = isinstance(s, basestring)
  else:
    is_string = isinstance(s, str)
  if not is_string:
    try:
      return str(s)
    except UnicodeEncodeError:
      if isinstance(s, Exception):
        # An Exception subclass containing non-ASCII data that doesn't
        # know how to print itself properly. We shouldn't raise a
        # further exception.
        return ' '.join([smart_str(arg) for arg in s])
      return unicode(s).encode(encoding, errors)
  elif isinstance(s, unicode):
    return s.encode(encoding, errors)
  else:
    return s


class TeamcityTestResult(TestResult):
  """
  Set ``_jb_do_not_call_enter_matrix`` to prevent it from runnig "enter matrix"
  """

  def __init__(self, stream=sys.stdout, *args, **kwargs):
    TestResult.__init__(self)
    for arg, value in kwargs.items():
      setattr(self, arg, value)
    self.output = stream
    self.messages = TeamcityServiceMessages(self.output, prepend_linebreak=True)
    if not "_jb_do_not_call_enter_matrix" in os.environ:
      self.messages.testMatrixEntered()
    self.current_failed = False
    self.current_suite = None
    self.subtest_suite = None

  def find_first(self, val):
    quot = val[0]
    count = 1
    quote_ind = val[count:].find(quot)
    while quote_ind != -1 and val[count + quote_ind - 1] == "\\":
      count = count + quote_ind + 1
      quote_ind = val[count:].find(quot)

    return val[0:quote_ind + count + 1]

  def find_second(self, val):
    val_index = val.find("!=")
    if val_index != -1:
      count = 1
      val = val[val_index + 2:].strip()
      quot = val[0]
      quote_ind = val[count:].find(quot)
      while quote_ind != -1 and val[count + quote_ind - 1] == "\\":
        count = count + quote_ind + 1
        quote_ind = val[count:].find(quot)
      return val[0:quote_ind + count + 1]

    else:
      quot = val[-1]
      quote_ind = val[:len(val) - 1].rfind(quot)
      while quote_ind != -1 and val[quote_ind - 1] == "\\":
        quote_ind = val[:quote_ind - 1].rfind(quot)
      return val[quote_ind:]

  def formatErr(self, err):
    exctype, value, tb = err
    return ''.join(traceback.format_exception(exctype, value, tb))

  def getTestName(self, test, is_subtest=False):
    if is_subtest:
      test_name = self.getTestName(test.test_case)
      return "{} {}".format(test_name, test._subDescription())
    if hasattr(test, '_testMethodName'):
      if test._testMethodName == "runTest":
        return str(test)
      return test._testMethodName
    else:
      test_name = str(test)
      whitespace_index = test_name.index(" ")
      if whitespace_index != -1:
        test_name = test_name[:whitespace_index]
      return test_name

  def getTestId(self, test):
    return test.id

  def addSuccess(self, test):
    TestResult.addSuccess(self, test)

  def addError(self, test, err):
    location = self.init_suite(test)
    self.current_failed = True
    TestResult.addError(self, test, err)

    err = self._exc_info_to_string(err, test)

    self.messages.testStarted(self.getTestName(test), location=location)
    self.messages.testError(self.getTestName(test),
                            message='Error', details=err, duration=self.__getDuration(test))

  def find_error_value(self, err):
    error_value = traceback.extract_tb(err)
    error_value = error_value[-1][-1]
    return error_value.split('assert')[-1].strip()

  def addFailure(self, test, err):
    location = self.init_suite(test)
    self.current_failed = True
    TestResult.addFailure(self, test, err)

    error_value = smart_str(err[1])
    if not len(error_value):
      # means it's test function and we have to extract value from traceback
      error_value = self.find_error_value(err[2])

    self_find_first = self.find_first(error_value)
    self_find_second = self.find_second(error_value)
    quotes = ["'", '"']
    if (self_find_first[0] == self_find_first[-1] and self_find_first[0] in quotes and
            self_find_second[0] == self_find_second[-1] and self_find_second[0] in quotes):
      # let's unescape strings to show sexy multiline diff in PyCharm.
      # By default all caret return chars are escaped by testing framework
      first = self._unescape(self_find_first)
      second = self._unescape(self_find_second)
    else:
      first = second = ""
    err = self._exc_info_to_string(err, test)

    self.messages.testStarted(self.getTestName(test), location=location)
    duration = self.__getDuration(test)
    self.messages.testFailed(self.getTestName(test),
                             message='Failure', details=err, expected=first, actual=second, duration=duration)

  def addSkip(self, test, reason):
    self.init_suite(test)
    self.current_failed = True
    self.messages.testIgnored(self.getTestName(test), message=reason)

  def _getSuite(self, test):
    try:
      suite = strclass(test.suite)
      suite_location = test.suite.location
      location = test.suite.abs_location
      if hasattr(test, "lineno"):
        location = location + ":" + str(test.lineno)
      else:
        location = location + ":" + str(test.test.lineno)
    except AttributeError:
      import inspect

      try:
        source_file = inspect.getsourcefile(test.__class__)
        if source_file:
          source_dir_splitted = source_file.split("/")[:-1]
          source_dir = "/".join(source_dir_splitted) + "/"
        else:
          source_dir = ""
      except TypeError:
        source_dir = ""

      suite = strclass(test.__class__)
      suite_location = "python_uttestid://" + source_dir + suite
      location = "python_uttestid://" + source_dir + str(test.id())

    return (suite, location, suite_location)

  def startTest(self, test):
    self.current_failed = False
    setattr(test, "startTime", datetime.datetime.now())

  def init_suite(self, test):
    suite, location, suite_location = self._getSuite(test)
    if suite != self.current_suite:
      if self.current_suite:
        self.messages.testSuiteFinished(self.current_suite)
      self.current_suite = suite
      self.messages.testSuiteStarted(self.current_suite, location=suite_location)
    return location

  def stopTest(self, test):
    duration = self.__getDuration(test)
    if not self.subtest_suite:
      if not self.current_failed:
        location = self.init_suite(test)
        self.messages.testStarted(self.getTestName(test), location=location)
        self.messages.testFinished(self.getTestName(test), duration=int(duration))
    else:
      self.messages.testSuiteFinished(self.subtest_suite)
      self.subtest_suite = None

  def __getDuration(self, test):
    start = getattr(test, "startTime", datetime.datetime.now())
    assert isinstance(start, datetime.datetime), \
      "You testcase has property named 'startTime' (value {0}). Please, rename it".format(start)
    d = datetime.datetime.now() - start
    duration = d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
    return duration

  def addSubTest(self, test, subtest, err):
    location = self.init_suite(test)
    suite_name = self.getTestName(test)  # + " (subTests)"
    if not self.subtest_suite:
      self.subtest_suite = suite_name
      self.messages.testSuiteStarted(self.subtest_suite, location=location)
    else:
      if suite_name != self.subtest_suite:
        self.messages.testSuiteFinished(self.subtest_suite)
        self.subtest_suite = suite_name
        self.messages.testSuiteStarted(self.subtest_suite, location=location)

    name = self.getTestName(subtest, True)
    if err is not None:
      error = self._exc_info_to_string(err, test)
      self.messages.testStarted(name)
      self.messages.testFailed(name, message='Failure', details=error, duration=None)
    else:
      self.messages.testStarted(name)
      self.messages.testFinished(name)


  def endLastSuite(self):
    if self.current_suite:
      self.messages.testSuiteFinished(self.current_suite)
      self.current_suite = None

  def _unescape(self, text):
    # do not use text.decode('string_escape'), it leads to problems with different string encodings given
    return text.replace("\\n", "\n")


class TeamcityTestRunner(object):
  def __init__(self, stream=sys.stdout):
    self.stream = stream

  def _makeResult(self, **kwargs):
    return TeamcityTestResult(self.stream, **kwargs)

  def run(self, test, **kwargs):
    result = self._makeResult(**kwargs)
    result.messages.testCount(test.countTestCases())
    test(result)
    result.endLastSuite()
    return result
