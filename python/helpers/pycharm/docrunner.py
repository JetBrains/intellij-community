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
      name = self.current_suite.name + test.source
      return name

    def getSuiteName(self, suite):
      if test.source.rfind(".") == -1:
        name = self.current_suite.name + test.source
      else:
        name = test.source
      return name

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
    Special runner for doctests,
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

from utrunner import debug, getModuleName, PYTHON_VERSION_MAJOR

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

def testfile(filename):
    text, filename = doctest._load_testfile(filename, None, False)

    name = os.path.basename(filename)
    globs = {}
    globs['__name__'] = '__main__'

    runner = DocTestRunner()
    parser = doctest.DocTestParser()
    # Read the file, convert it to a test, and run it.
    test = parser.get_doctest(text, globs, name, filename, 0)
    if test.examples:
      runner.run(test)

def walkModules(modules, dirname, names):
  walkModulesUsingPattern(modules, dirname, names)

def walkModulesUsingPattern(modules, dirname, names, pattern = ".*"):
  prog = re.compile(pattern)
  
  for name in names:
    path = os.path.join(dirname, name)
    if prog.match(name):
      if name.endswith(".py"):
        modules.append(loadSource(path))
      elif not name.endswith(".pyc") and not name.endswith("$py.class")\
                                                  and os.path.isfile(path):
        testfile(path)

def testFilesInFolder(folder):
  return testFilesInFolderUsingPattern(folder)

def testFilesInFolderUsingPattern(folder, pattern = ".*"):
  ''' loads modules from folder ,
      check if module name matches given pattern'''
  modules = []
  result = []
  prog = re.compile(pattern)

  if PYTHON_VERSION_MAJOR == 3:
    for root, dirs, files in os.walk(folder, walkModules, modules):
      for name in files:
        path = os.path.join(root, name)
        if prog.match(name):
          if name.endswith(".py"):
            modules.append(loadSource(path))
          elif not name.endswith(".pyc") and not name.endswith("$py.class")\
                                  and os.path.isfile(path):
            testfile(path)
  else:
    os.path.walk(folder, walkModulesUsingPattern, modules)

  for module in modules:
    if prog.match(module.__name__):
      result.append(module)
  return result

if __name__ == "__main__":  
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
          modules = testFilesInFolderUsingPattern(a_splitted[0], a_splitted[1])
      else:
        if a[0].endswith("/"):
          debug("/ from folder " + a[0])
          modules = testFilesInFolder(a[0])
        else:
          # from file
          debug("/ from module " + a[0])
          # for doctests from non-python file
          if a[0].rfind(".py") == -1:
              testfile(a[0])
              modules = []
          else:
              modules = [loadSource(a[0])]

      # for doctests
      for module in modules:
        tests = finder.find(module, module.__name__)
        for test in tests:
          if test.examples:
            runner.run(test)

    elif len(a) == 2:
      # From testcase
      debug("/ from class " + a[1] + " in " + a[0])
      try:
        module = loadSource(a[0])
      except SyntaxError:
        raise NameError('File "%s" is not python file' % (a[0], ))
      if hasattr(module, a[1]):
        testcase = getattr(module, a[1])
        tests = finder.find(testcase, testcase.__name__)
        for test in tests:
            runner.run(test)
      else:
        raise NameError('Module "%s" has no class "%s"' % (a[0], a[1]))
    else:
      # From method in class or from function
      try:
        module = loadSource(a[0])
      except SyntaxError:
        raise NameError('File "%s" is not python file' % (a[0], ))
      if a[1] == "":
          # test function, not method
          debug("/ from method " + a[2] + " in " + a[0])
          if hasattr(module, a[2]):
            testcase = getattr(module, a[2])
            tests = finder.find(testcase, testcase.__name__)
            for test in tests:
              runner.run(test)
          else:
            raise NameError('Module "%s" has no method "%s"' % (a[0], a[2]))
      else:
          debug("/ from method " + a[2] + " in class " +  a[1] + " in " + a[0])
          if hasattr(module, a[1]):
            testCaseClass = getattr(module, a[1])
            if hasattr(testCaseClass, a[2]):
              testcase = getattr(testCaseClass, a[2])
              tests = finder.find(testcase, testcase.__name__)
              for test in tests:
                runner.run(test)
            else:
              raise NameError('Class "%s" has no function "%s"' % (testCaseClass, a[2]))
          else:
            raise NameError('Module "%s" has no class "%s"' % (module, a[1]))