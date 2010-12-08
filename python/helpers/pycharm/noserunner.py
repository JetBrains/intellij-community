import sys, traceback
from tcmessages import TeamcityServiceMessages
from utrunner import debug
from tcunittest import strclass
import datetime

try:
  from nose.core import TestProgram
  from nose.plugins.base import Plugin
except:
  raise NameError("Please, install nosetests")

class TeamcityPlugin(Plugin):
  enabled = True
  name = 'teamcity'

  def __init__(self, stream=sys.stdout):
    self.output = stream
    self.current_suite = None
    self.messages = TeamcityServiceMessages(self.output, prepend_linebreak=True)

  def configure(self, options, conf):
   if not self.can_configure:
     return
   self.conf = conf

  def getTestName(self, test):
    test_name_full = str(test)
    ind_1 = test_name_full.rfind('(')
    if ind_1 != -1:
      return test_name_full[:ind_1]
    ind = test_name_full.rfind('.')
    if ind != -1:
      return test_name_full[test_name_full.rfind(".") + 1:]
    return test_name_full

  def getSuiteName(self, test):
    test_name_full = str(test)
    ind_1 = test_name_full.rfind('(')
    if ind_1 != -1:
      return test_name_full[ind_1+1: -1]
    ind = test_name_full.rfind('.')
    if ind != -1:
      return test_name_full[:test_name_full.rfind(".")]
    return test_name_full

  def startTest(self, test):
    location, suite_location = self.__getSuite(test)
    suite = self.getSuiteName(test)
    if suite != self.current_suite:
        if self.current_suite:
            self.messages.testSuiteFinished(self.current_suite)
        self.current_suite = suite
        self.messages.testSuiteStarted(self.current_suite, location=suite_location)
    setattr(test, "startTime", datetime.datetime.now())
    self.messages.testStarted(self.getTestName(test), location=location)

  def stopTest(self, test):
    start = getattr(test, "startTime", datetime.datetime.now())
    d = datetime.datetime.now() - start
    duration=d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
    self.messages.testFinished(self.getTestName(test), duration=int(duration))

  def addFailure(self, test, err):
    err = self.formatErr(err)
    self.messages.testFailed(self.getTestName(test),
                             message='Failure', details=err)

  def addSkip(self, test):
    self.messages.testIgnored(self.getTestName(test))

  def addError(self, test, err):
    err = self.formatErr(err)
    self.messages.testFailed(self.getTestName(test),
                             message='Error', details=err)

  def finalize(self, result):
    if self.current_suite:
      self.messages.testSuiteFinished(self.current_suite)
      self.current_suite = None

  def __getSuite(self, test):
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
      suite_location = "python_uttestid://" + suite
      location = "python_uttestid://" + str(test.id())

    return (location, suite_location)

  def formatErr(self, err):
    exctype, value, tb = err
    return ''.join(traceback.format_exception(exctype, value, tb))

def process_args():
  tests = []

  if sys.argv != 0:
    arg = sys.argv[1].strip()
    if len(arg) == 0:
      return

    a = arg.split("::")
    if len(a) == 1:
      # From module or folder
      a_splitted = a[0].split(";")
      if len(a_splitted) != 1:
        # means we have pattern to match against
        if a_splitted[0].endswith("/"):
          debug("/ from folder " + a_splitted[0] + ". Use pattern: " + a_splitted[1])
          tests.append(a_splitted[0])
      else:
        if a[0].endswith("/"):
          debug("/ from folder " + a[0])
          tests.append(a[0])
        else:
          debug("/ from module " + a[0])
          tests.append(a[0])

    elif len(a) == 2:
      # From testcase
      debug("/ from testcase " + a[1] + " in " + a[0])
      tests.append(a[0] + ":" + a[1])
    else:
      # From method in class or from function
      debug("/ from method " + a[2] + " in testcase " +  a[1] + " in " + a[0])
      if a[1] == "":
          # test function, not method
          tests.append(a[0] + ":" + a[2])
      else:
          tests.append(a[0] + ":" + a[1] + "." + a[2])

    argv = ['nosetests']

    if len(sys.argv) > 2:
      options = sys.argv[2].split()
      argv.extend(options)

    argv.extend(tests)
    TestProgram(argv=argv, addplugins=[TeamcityPlugin()])

if __name__ == "__main__":
  process_args()