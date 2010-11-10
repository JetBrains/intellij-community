import os
import imp
import sys
import re
import doctest
import traceback
import datetime

from tcunittest import TeamcityTestResult

class TeamcityDocTestResult(TeamcityTestResult):
    """
    DocTests Result extends TeamcityTestResult,
    overrides some methods, specific for doc tests,
    such as getTestName, getTestId.
    """
    def getTestName(self, test):
        print test.__dict__
        return test.source

    def getTestId(self, test):
        return "file:///" + self.current_suite.filename + ":" + str( self.current_suite.lineno + test.lineno)

    def startTest(self, test):
        setattr(test, "startTime", datetime.datetime.now())
        id = self.getTestId(test)
        self.messages.testStarted(self.getTestName(test), location=id)

    def startSuite(self, suite):
        self.current_suite = suite
        self.messages.testSuiteStarted(suite.name)

    def stopSuite(self, suite):
        self.messages.testSuiteFinished(suite.name)

    def addFailure(self, test, err = ''):
        self.messages.testFailed(self.getTestName(test),
            message='Failure', details=err)

    def addError(self, test, err = ''):
        self.messages.testFailed(self.getTestName(test),
            message='Error', details=err)

class DocTestRunner(doctest.DocTestRunner):
    """
    Specil runner for doctests,
    overrides __run method to report results using TeamcityDocTestResult
    """
    def __init__(self, verbose=None, optionflags=0):
        doctest.DocTestRunner.__init__(self, verbose, optionflags)
        self.stream = sys.stdout
        self.result = TeamcityDocTestResult(self.stream)

    def __run(self, test, compileflags, out):
        SUCCESS, FAILURE, BOOM = range(3) # `outcome` state
        check = self._checker.check_output

        self.result.startSuite(test)
        for examplenum, example in enumerate(test.examples):
            if example.options:
                for (optionflag, val) in example.options.items():
                    if val:
                        self.optionflags |= optionflag
                    else:
                        self.optionflags &= ~optionflag

            if self.optionflags & doctest.SKIP:
                continue

            filename = '<doctest %s[%d]>' % (test.name, examplenum)

            try:
                exec compile(example.source, filename, "single",
                             compileflags, 1) in test.globs
                self.debugger.set_continue()
                exception = None
            except KeyboardInterrupt:
                raise
            except:
                exception = sys.exc_info()
                self.debugger.set_continue()

            got = self._fakeout.getvalue()
            self._fakeout.truncate(0)
            outcome = FAILURE

            if exception is None:
                if check(example.want, got, self.optionflags):
                    outcome = SUCCESS

            else:
                exc_info = sys.exc_info()
                exc_msg = traceback.format_exception_only(*exc_info[:2])[-1]
                got += doctest._exception_traceback(exc_info)

                if example.exc_msg is None:
                    outcome = BOOM

                elif check(example.exc_msg, exc_msg, self.optionflags):
                    outcome = SUCCESS

                elif self.optionflags & IGNORE_EXCEPTION_DETAIL:
                    m1 = re.match(r'[^:]*:', example.exc_msg)
                    m2 = re.match(r'[^:]*:', exc_msg)
                    if m1 and m2 and check(m1.group(0), m2.group(0),
                                           self.optionflags):
                        outcome = SUCCESS

            # Report the outcome.
            if outcome is SUCCESS:
                self.result.startTest(example)
                self.result.stopTest(example)
            elif outcome is FAILURE:
                self.result.startTest(example)
                err = self._failure_header(test, example) + \
                            self._checker.output_difference(example, got, self.optionflags)
                self.result.addFailure(example, err)

            elif outcome is BOOM:
                self.result.startTest(example)
                err = self._failure_header(test, example) + \
                                'Exception raised:\n' + doctest._indent(doctest._exception_traceback(exc_info))
                self.result.addError(example, err)
            else:
                assert False, ("unknown outcome", outcome)

        self.result.stopSuite(test)


modules = {}

from utrunner import debug, getModuleName, loadModulesFromFolderRec, loadModulesFromFolderUsingPattern

def loadSource(fileName):
  """
  loads source from fileName,
  we can't use tat function from utrunner, because of we
  store modules in global variable.
  """
  baseName = os.path.basename(fileName)
  moduleName = os.path.splitext(baseName)[0]
  if moduleName in modules: # add unique number to prevent name collisions
    cnt = 2
    prefix = moduleName
    while getModuleName(prefix, cnt) in modules:
      cnt += 1
    moduleName = getModuleName(prefix, cnt)
  debug("/ Loading " + fileName + " as " + moduleName)
  module = imp.load_source(moduleName, fileName)
  modules[moduleName] = module
  return module

runner = DocTestRunner()
finder = doctest.DocTestFinder()

for arg in sys.argv[1:]:
  arg = arg.strip()
  if len(arg) == 0:
    continue

  a = arg.split("::")
  if len(a) == 1:
    # From module or folder
    a_splitted = a[0].split(";")
    if len(a_splitted) != 1:
      # means we have pattern to match against
      if a_splitted[0].endswith("/"):
        debug("/ from folder " + a_splitted[0] + ". Use pattern: " + a_splitted[1])
        modules = loadModulesFromFolderUsingPattern(a_splitted[0], a_splitted[1])
    else:
      if a[0].endswith("/"):
        debug("/ from folder " + a[0])
        modules = loadModulesFromFolderRec(a[0])
      else:
        debug("/ from module " + a[0])
        modules = [loadSource(a[0])]

    # for doctests
    for module in modules:
      tests = finder.find(module, module.__name__)
      for test in tests:
        runner.run(test)

  elif len(a) == 2:
    # From testcase
    debug("/ from class " + a[1] + " in " + a[0])
    module = loadSource(a[0])
    testcase = getattr(module, a[1])
    tests = finder.find(testcase, testcase.__name__)
    for test in tests:
        runner.run(test)
  else:
    # From method in class or from function
    module = loadSource(a[0])
    if a[1] == "":
        # test function, not method
        debug("/ from method " + a[2] + " in " + a[0])
        testcase = getattr(module, a[2])
        tests = finder.find(testcase, testcase.__name__)
        for test in tests:
            runner.run(test)
    else:
        debug("/ from method " + a[2] + " in class " +  a[1] + " in " + a[0])
        testCaseClass = getattr(module, a[1])
        testcase = getattr(testCaseClass, a[2])
        tests = finder.find(testcase, testcase.__name__)
        for test in tests:
            runner.run(test)