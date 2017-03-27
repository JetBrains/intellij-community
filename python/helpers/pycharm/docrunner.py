import imp
import sys
import datetime
import os
helpers_dir = os.getenv("PYCHARM_HELPERS_DIR", sys.path[0])
if sys.path[0] != helpers_dir:
    sys.path.insert(0, helpers_dir)

from tcunittest import TeamcityTestResult
from tcmessages import TeamcityServiceMessages

from pycharm_run_utils import import_system_module
from pycharm_run_utils import adjust_sys_path, debug, getModuleName, PYTHON_VERSION_MAJOR

adjust_sys_path()

re = import_system_module("re")
doctest = import_system_module("doctest")
traceback = import_system_module("traceback")

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
    file = os.path.realpath(self.current_suite.filename) if self.current_suite.filename else ""
    line_no = test.lineno
    if self.current_suite.lineno:
      line_no += self.current_suite.lineno
    return "file://" + file + ":" + str(line_no)

  def getSuiteLocation(self):
    file = os.path.realpath(self.current_suite.filename) if self.current_suite.filename else ""
    location = "file://" + file
    if self.current_suite.lineno:
      location += ":" + str(self.current_suite.lineno)
    return location

  def startTest(self, test):
    setattr(test, "startTime", datetime.datetime.now())
    id = self.getTestId(test)
    self.messages.testStarted(self.getTestName(test), location=id)

  def startSuite(self, suite):
    self.current_suite = suite
    self.messages.testSuiteStarted(suite.name, location=self.getSuiteLocation())

  def stopSuite(self, suite):
    self.messages.testSuiteFinished(suite.name)

  def addFailure(self, test, err = '', expected=None, actual=None):
    self.messages.testFailed(self.getTestName(test), expected=expected, actual=actual,
      message='Failure', details=err, duration=int(self.__getDuration(test)))

  def addError(self, test, err = ''):
    self.messages.testError(self.getTestName(test),
      message='Error', details=err, duration=self.__getDuration(test))

  def stopTest(self, test):
    duration = self.__getDuration(test)
    self.messages.testFinished(self.getTestName(test), duration=int(duration))

  def __getDuration(self, test):
    start = getattr(test, "startTime", datetime.datetime.now())
    d = datetime.datetime.now() - start
    duration = d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
    return duration


class DocTestRunner(doctest.DocTestRunner):
  """
  Special runner for doctests,
  overrides __run method to report results using TeamcityDocTestResult
  """
  def __init__(self, verbose=None, optionflags=0):
    doctest.DocTestRunner.__init__(self, verbose, optionflags)
    self.stream = sys.stdout
    self.result = TeamcityDocTestResult(self.stream)
    #self.result.messages.testMatrixEntered()
    self._tests = []

  def addTests(self, tests):
    self._tests.extend(tests)

  def addTest(self, test):
    self._tests.append(test)

  def countTests(self):
    return len(self._tests)

  def start(self):
    for test in self._tests:
      self.run(test)

  def __run(self, test, compileflags, out):
    failures = tries = 0

    original_optionflags = self.optionflags
    SUCCESS, FAILURE, BOOM = range(3) # `outcome` state
    check = self._checker.check_output
    self.result.startSuite(test)
    for examplenum, example in enumerate(test.examples):

      quiet = (self.optionflags & doctest.REPORT_ONLY_FIRST_FAILURE and
               failures > 0)

      self.optionflags = original_optionflags
      if example.options:
        for (optionflag, val) in example.options.items():
          if val:
            self.optionflags |= optionflag
          else:
            self.optionflags &= ~optionflag

      if hasattr(doctest, 'SKIP'):
        if self.optionflags & doctest.SKIP:
          continue

      tries += 1
      if not quiet:
        self.report_start(out, test, example)

      filename = '<doctest %s[%d]>' % (test.name, examplenum)

      try:
        exec(compile(example.source, filename, "single",
          compileflags, 1), test.globs)
        self.debugger.set_continue() # ==== Example Finished ====
        exception = None
      except KeyboardInterrupt:
        raise
      except:
        exception = sys.exc_info()
        self.debugger.set_continue() # ==== Example Finished ====

      got = self._fakeout.getvalue()  # the actual output
      self._fakeout.truncate(0)
      outcome = FAILURE   # guilty until proved innocent or insane

      if exception is None:
        if check(example.want, got, self.optionflags):
          outcome = SUCCESS

      else:
        exc_msg = traceback.format_exception_only(*exception[:2])[-1]
        if not quiet:
          got += doctest._exception_traceback(exception)

        if example.exc_msg is None:
          outcome = BOOM

        elif check(example.exc_msg, exc_msg, self.optionflags):
          outcome = SUCCESS

        elif self.optionflags & doctest.IGNORE_EXCEPTION_DETAIL:
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
        err = self._failure_header(test, example) +\
              self._checker.output_difference(example, got, self.optionflags)
        expected = getattr(example, "want", None)
        self.result.addFailure(example, err, expected=expected, actual=got)

      elif outcome is BOOM:
        self.result.startTest(example)
        err=self._failure_header(test, example) +\
            'Exception raised:\n' + doctest._indent(doctest._exception_traceback(exception))
        self.result.addError(example, err)

      else:
        assert False, ("unknown outcome", outcome)

    self.optionflags = original_optionflags

    self.result.stopSuite(test)


modules = {}



runner = DocTestRunner()

def loadSource(fileName):
  """
  loads source from fileName,
  we can't use tat function from utrunner, because of we
  store modules in global variable.
  """
  baseName = os.path.basename(fileName)
  moduleName = os.path.splitext(baseName)[0]

  # for users wanted to run simple doctests under django
  #because of django took advantage of module name
  settings_file = os.getenv('DJANGO_SETTINGS_MODULE')
  if settings_file and moduleName=="models":
    baseName = os.path.realpath(fileName)
    moduleName = ".".join((baseName.split(os.sep)[-2], "models"))

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
  if PYTHON_VERSION_MAJOR == 3:
    text, filename = doctest._load_testfile(filename, None, False, "utf-8")
  else:
    text, filename = doctest._load_testfile(filename, None, False)

  name = os.path.basename(filename)
  globs = {'__name__': '__main__'}

  parser = doctest.DocTestParser()
  # Read the file, convert it to a test, and run it.
  test = parser.get_doctest(text, globs, name, filename, 0)
  if test.examples:
    runner.addTest(test)

def testFilesInFolder(folder):
  return testFilesInFolderUsingPattern(folder)

def testFilesInFolderUsingPattern(folder, pattern = ".*"):
  ''' loads modules from folder ,
      check if module name matches given pattern'''
  modules = []
  prog = re.compile(pattern)

  for root, dirs, files in os.walk(folder):
    for name in files:
      path = os.path.join(root, name)
      if prog.match(name):
        if name.endswith(".py"):
          modules.append(loadSource(path))
        elif not name.endswith(".pyc") and not name.endswith("$py.class") and os.path.isfile(path):
          testfile(path)

    return modules

if __name__ == "__main__":
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
            runner.addTest(test)

    elif len(a) == 2:
      # From testcase
      debug("/ from class " + a[1] + " in " + a[0])
      try:
        module = loadSource(a[0])
      except SyntaxError:
        raise NameError('File "%s" is not python file' % (a[0], ))
      if hasattr(module, a[1]):
        testcase = getattr(module, a[1])
        tests = finder.find(testcase, getattr(testcase, "__name__", None))
        runner.addTests(tests)
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
          tests = finder.find(testcase, getattr(testcase, "__name__", None))
          runner.addTests(tests)
        else:
          raise NameError('Module "%s" has no method "%s"' % (a[0], a[2]))
      else:
        debug("/ from method " + a[2] + " in class " +  a[1] + " in " + a[0])
        if hasattr(module, a[1]):
          testCaseClass = getattr(module, a[1])
          if hasattr(testCaseClass, a[2]):
            testcase = getattr(testCaseClass, a[2])
            name = getattr(testcase, "__name__", None)
            if not name:
              name = testCaseClass.__name__
            tests = finder.find(testcase, name)
            runner.addTests(tests)
          else:
            raise NameError('Class "%s" has no function "%s"' % (testCaseClass, a[2]))
        else:
          raise NameError('Module "%s" has no class "%s"' % (module, a[1]))

  debug("/ Loaded " + str(runner.countTests()) + " tests")
  TeamcityServiceMessages(sys.stdout).testCount(runner.countTests())
  runner.start()