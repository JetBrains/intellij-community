import sys, os
import imp

helpers_dir = os.getenv("PYCHARM_HELPERS_DIR", sys.path[0])
if sys.path[0] != helpers_dir:
    sys.path.insert(0, helpers_dir)

from tcunittest import TeamcityTestResult

from pycharm_run_utils import import_system_module
from pycharm_run_utils import adjust_sys_path
from pycharm_run_utils import debug, getModuleName

adjust_sys_path()

re = import_system_module("re")
inspect = import_system_module("inspect")

try:
  from attest.reporters import AbstractReporter
  from attest.collectors import Tests
  from attest import TestBase
except:
  raise NameError("Please, install attests")

class TeamCityReporter(AbstractReporter, TeamcityTestResult):
  """Teamcity reporter for attests."""

  def __init__(self, prefix):
    TeamcityTestResult.__init__(self)
    self.prefix = prefix

  def begin(self, tests):
    """initialize suite stack and count tests"""
    self.total = len(tests)
    self.suite_stack = []
    self.messages.testCount(self.total)

  def success(self, result):
    """called when test finished successfully"""
    suite = self.get_suite_name(result.test)
    self.start_suite(suite)
    name = self.get_test_name(result)
    self.start_test(result, name)
    self.messages.testFinished(name)

  def failure(self, result):
    """called when test failed"""
    suite = self.get_suite_name(result.test)
    self.start_suite(suite)
    name = self.get_test_name(result)
    self.start_test(result, name)
    exctype, value, tb = result.exc_info
    error_value = self.find_error_value(tb)
    if (error_value.startswith("'") or error_value.startswith('"')) and\
       (error_value.endswith("'") or error_value.endswith('"')):
      first = self._unescape(self.find_first(error_value))
      second = self._unescape(self.find_second(error_value))
    else:
      first = second = ""

    err = self.formatErr(result.exc_info)
    if isinstance(result.error, AssertionError):
      self.messages.testFailed(name, message='Failure',
        details=err,
        expected=first, actual=second)
    else:
      self.messages.testError(name, message='Error',
        details=err)

  def finished(self):
    """called when all tests finished"""
    self.end_last_suite()
    for suite in self.suite_stack[::-1]:
      self.messages.testSuiteFinished(suite)

  def get_test_name(self, result):
    name = result.test_name
    ind = name.find("%")    #remove unique module prefix
    if ind != -1:
      name = name[:ind]+name[name.find(".", ind):]
    return name

  def end_last_suite(self):
    if self.current_suite:
      self.messages.testSuiteFinished(self.current_suite)
      self.current_suite = None

  def get_suite_name(self, test):
    module = inspect.getmodule(test)
    klass = getattr(test, "im_class", None)
    file = module.__file__
    if file.endswith("pyc"):
      file = file[:-1]

    suite = module.__name__
    if self.prefix:
      tmp = file[:-3]
      ind = tmp.split(self.prefix)[1]
      suite = ind.replace("/", ".")
    if klass:
      suite += "." + klass.__name__
      lineno = inspect.getsourcelines(klass)
    else:
      lineno = ("", 1)

    return (suite, file+":"+str(lineno[1]))

  def start_suite(self, suite_info):
    """finish previous suite and put current suite
        to stack"""
    suite, file = suite_info
    if suite != self.current_suite:
      if self.current_suite:
        if suite.startswith(self.current_suite+"."):
          self.suite_stack.append(self.current_suite)
        else:
          self.messages.testSuiteFinished(self.current_suite)
          for s in self.suite_stack:
            if not suite.startswith(s+"."):
              self.current_suite = s
              self.messages.testSuiteFinished(self.current_suite)
            else:
              break
      self.current_suite = suite
      self.messages.testSuiteStarted(self.current_suite, location="file://" + file)

  def start_test(self, result, name):
    """trying to find test location """
    real_func = result.test.func_closure[0].cell_contents
    lineno = inspect.getsourcelines(real_func)
    file = inspect.getsourcefile(real_func)
    self.messages.testStarted(name, "file://"+file+":"+str(lineno[1]))

def get_subclasses(module, base_class=TestBase):
  test_classes = []
  for name in dir(module):
    obj = getattr(module, name)
    try:
      if issubclass(obj, base_class):
        test_classes.append(obj)
    except TypeError:  # If 'obj' is not a class
      pass
  return test_classes

def get_module(file_name):
  baseName = os.path.splitext(os.path.basename(file_name))[0]
  return imp.load_source(baseName, file_name)

modules = {}
def getModuleName(prefix, cnt):
  """ adds unique number to prevent name collisions"""
  return prefix + "%" + str(cnt)

def loadSource(fileName):
  baseName = os.path.basename(fileName)
  moduleName = os.path.splitext(baseName)[0]

  if moduleName in modules:
    cnt = 2
    prefix = moduleName
    while getModuleName(prefix, cnt) in modules:
      cnt += 1
    moduleName = getModuleName(prefix, cnt)
  debug("/ Loading " + fileName + " as " + moduleName)
  module = imp.load_source(moduleName, fileName)
  modules[moduleName] = module
  return module


def register_tests_from_module(module, tests):
  """add tests from module to main test suite"""
  tests_to_register = []

  for i in dir(module):
    obj = getattr(module, i)
    if isinstance(obj, Tests):
      tests_to_register.append(i)

  for i in tests_to_register:
    baseName = module.__name__+"."+i
    tests.register(baseName)
  test_subclasses = get_subclasses(module)
  if test_subclasses:
    for subclass in test_subclasses:
      tests.register(subclass())


def register_tests_from_folder(tests, folder, pattern=None):
  """add tests from folder to main test suite"""
  listing = os.listdir(folder)
  files = listing
  if pattern: #get files matched given pattern
    prog_list = [re.compile(pat.strip()) for pat in pattern.split(',')]
    files = []
    for fileName in listing:
      if os.path.isdir(folder+fileName):
        files.append(fileName)
      for prog in prog_list:
        if prog.match(fileName):
          files.append(fileName)

  if not folder.endswith("/"):
    folder += "/"
  for fileName in files:
    if os.path.isdir(folder+fileName):
      register_tests_from_folder(tests, folder+fileName, pattern)
    if not fileName.endswith("py"):
      continue

    module = loadSource(folder+fileName)
    register_tests_from_module(module, tests)

def process_args():
  tests = Tests()
  prefix = ""
  if not sys.argv:
    return

  arg = sys.argv[1].strip()
  if not len(arg):
    return

  argument_list = arg.split("::")
  if len(argument_list) == 1:
    # From module or folder
    a_splitted = argument_list[0].split(";")
    if len(a_splitted) != 1:
      # means we have pattern to match against
      if a_splitted[0].endswith("/"):
        debug("/ from folder " + a_splitted[0] + ". Use pattern: " + a_splitted[1])
        prefix = a_splitted[0]
        register_tests_from_folder(tests, a_splitted[0], a_splitted[1])
    else:
      if argument_list[0].endswith("/"):
        debug("/ from folder " + argument_list[0])
        prefix = a_splitted[0]
        register_tests_from_folder(tests, argument_list[0])
      else:
        debug("/ from file " + argument_list[0])
        module = get_module(argument_list[0])
        register_tests_from_module(module, tests)

  elif len(argument_list) == 2:
    # From testcase
    debug("/ from test class " + argument_list[1] + " in " + argument_list[0])
    module = get_module(argument_list[0])
    klass = getattr(module, argument_list[1])
    tests.register(klass())
  else:
    # From method in class or from function
    module = get_module(argument_list[0])
    if argument_list[1] == "":
      debug("/ from function " + argument_list[2] + " in " + argument_list[0])
      # test function, not method
      test = getattr(module, argument_list[2])
    else:
      debug("/ from method " + argument_list[2] + " in class " +  argument_list[1] + " in " + argument_list[0])
      klass = getattr(module, argument_list[1])
      test = getattr(klass(), argument_list[2])
    tests.register([test])

  tests.run(reporter=TeamCityReporter(prefix))

if __name__ == "__main__":
  process_args()