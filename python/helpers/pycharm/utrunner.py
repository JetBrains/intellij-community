import sys
import imp
import os
import fnmatch

roots = sys.path[:]

helpers_dir = os.getenv("PYCHARM_HELPERS_DIR", sys.path[0])
if sys.path[0] != helpers_dir:
  sys.path.insert(0, helpers_dir)

from tcunittest import TeamcityTestRunner
from nose_helper import TestLoader, ContextSuite
from pycharm_run_utils import import_system_module
from pycharm_run_utils import adjust_sys_path
from pycharm_run_utils import debug, getModuleName, PYTHON_VERSION_MAJOR

adjust_sys_path()

os = import_system_module("os")
re = import_system_module("re")

modules = {}

def loadSource(fileName):
  baseName = os.path.basename(fileName)
  moduleName = os.path.splitext(baseName)[0]

  if os.path.isdir(fileName):
    fileName = fileName.rstrip('/\\') + os.path.sep

  # for users wanted to run unittests under django
  # because of django took advantage of module name
  settings_file = os.getenv('DJANGO_SETTINGS_MODULE')

  if settings_file and moduleName == "models":
    baseName = os.path.realpath(fileName)
    moduleName = ".".join((baseName.split(os.sep)[-2], "models"))
  else:
    path = fileName
    for p in roots:
      # Python 2.6+
      try:
        rel_path = os.path.relpath(fileName, start=p)
        if rel_path.find('..') == -1 and len(rel_path) < len(path):
          path = rel_path
      except:
        pass # relpath can raise an error in case of different drives for a path and start on Windows

    if path.endswith('.py'):
      path = path[0:-3]

    moduleName = path.replace('/', '.').replace('\\', '.')

  if moduleName in modules and len(sys.argv[1:-1]) == 1:  # add unique number to prevent name collisions
    cnt = 2
    prefix = moduleName
    while getModuleName(prefix, cnt) in modules:
      cnt += 1
    moduleName = getModuleName(prefix, cnt)

  debug("/ Loading " + fileName + " as " + moduleName)

  try:
    module = imp.load_source(moduleName, fileName)
  except SystemError:  # probably failed because of the relative imports
    # first we import module with all its parents
    __import__(moduleName)

    # then load it by filename to be sure it is the one we need
    module = imp.load_source(moduleName, fileName)

  modules[moduleName] = module
  return module

def walkModules(modulesAndPattern, dirname, names):
  modules = modulesAndPattern[0]
  pattern = modulesAndPattern[1]
  # fnmatch converts glob to regexp
  prog_list = [re.compile(fnmatch.translate(pat.strip())) for pat in pattern.split(',')]
  for name in names:
    for prog in prog_list:
      if name.endswith(".py") and prog.match(name):
        modules.append(loadSource(os.path.join(dirname, name)))


# For default pattern see https://docs.python.org/2/library/unittest.html#test-discovery
def loadModulesFromFolderRec(folder, pattern="test*.py"):
  modules = []
  # fnmatch converts glob to regexp
  prog_list = [re.compile(fnmatch.translate(pat.strip())) for pat in pattern.split(',')]
  for root, dirs, files in os.walk(folder):
    files = [f for f in files if not f[0] == '.']
    dirs[:] = [d for d in dirs if not d[0] == '.']
    for name in files:
      for prog in prog_list:
        if name.endswith(".py") and prog.match(name):
          modules.append(loadSource(os.path.join(root, name)))
  return modules

testLoader = TestLoader()
all = ContextSuite()
pure_unittest = False

def setLoader(module):
  global testLoader, all
  try:
    module.__getattribute__('unittest2')
    import unittest2

    testLoader = unittest2.TestLoader()
    all = unittest2.TestSuite()
  except:
    pass

if __name__ == "__main__":
  arg = sys.argv[-1]
  if arg == "true":
    import unittest

    testLoader = unittest.TestLoader()
    all = unittest.TestSuite()
    pure_unittest = True

    if len(sys.argv) == 2:  # If folder not provided, we need pretend folder is current
     sys.argv.insert(1, ".")

  options = {}
  for arg in sys.argv[1:-1]:
    arg = arg.strip()
    if len(arg) == 0:
      continue

    if arg.startswith("--"):
      options[arg[2:]] = True
      continue

    a = arg.split("::")
    if len(a) == 1:
      # From module or folder
      a_splitted = a[0].split("_args_separator_")  # ";" can't be used with bash, so we use "_args_separator_"
      if len(a_splitted) != 1:
        # means we have pattern to match against
        if os.path.isdir(a_splitted[0]):
          debug("/ from folder " + a_splitted[0] + ". Use pattern: " + a_splitted[1])
          modules = loadModulesFromFolderRec(a_splitted[0], a_splitted[1])
      else:
        if os.path.isdir(a[0]):
          debug("/ from folder " + a[0])
          modules = loadModulesFromFolderRec(a[0])
        else:
          debug("/ from module " + a[0])
          modules = [loadSource(a[0])]

      for module in modules:
        all.addTests(testLoader.loadTestsFromModule(module))

    elif len(a) == 2:
      # From testcase
      debug("/ from testcase " + a[1] + " in " + a[0])
      module = loadSource(a[0])
      setLoader(module)

      if pure_unittest:
        all.addTests(testLoader.loadTestsFromTestCase(getattr(module, a[1])))
      else:
        all.addTests(testLoader.loadTestsFromTestClass(getattr(module, a[1])),
                     getattr(module, a[1]))
    else:
      # From method in class or from function
      debug("/ from method " + a[2] + " in testcase " + a[1] + " in " + a[0])
      module = loadSource(a[0])
      setLoader(module)

      if a[1] == "":
        # test function, not method
        all.addTest(testLoader.makeTest(getattr(module, a[2])))
      else:
        testCaseClass = getattr(module, a[1])
        try:
          all.addTest(testCaseClass(a[2]))
        except:
          # class is not a testcase inheritor
          all.addTest(
            testLoader.makeTest(getattr(testCaseClass, a[2]), testCaseClass))

  debug("/ Loaded " + str(all.countTestCases()) + " tests")
  TeamcityTestRunner().run(all, **options)
