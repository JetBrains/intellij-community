import sys
import imp
import os

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

  # for users wanted to run unittests under django
  #because of django took advantage of module name
  settings_file = os.getenv('DJANGO_SETTINGS_MODULE')
  if settings_file and moduleName == "models":
    baseName = os.path.realpath(fileName)
    moduleName = ".".join((baseName.split(os.sep)[-2], "models"))

  if moduleName in modules and len(sys.argv[1:-1]) == 1: # add unique number to prevent name collisions
    cnt = 2
    prefix = moduleName
    while getModuleName(prefix, cnt) in modules:
      cnt += 1
    moduleName = getModuleName(prefix, cnt)
  debug("/ Loading " + fileName + " as " + moduleName)
  module = imp.load_source(moduleName, fileName)
  modules[moduleName] = module
  return module

def walkModules(modulesAndPattern, dirname, names):
  modules = modulesAndPattern[0]
  pattern = modulesAndPattern[1]
  prog_list = [re.compile(pat.strip()) for pat in pattern.split(',')]
  for name in names:
    for prog in prog_list:
      if name.endswith(".py") and prog.match(name):
        modules.append(loadSource(os.path.join(dirname, name)))

def loadModulesFromFolderRec(folder, pattern = "test.*"):
  modules = []
  if PYTHON_VERSION_MAJOR == 3:
    prog_list = [re.compile(pat.strip()) for pat in pattern.split(',')]
    for root, dirs, files in os.walk(folder):
      for name in files:
        for prog in prog_list:
          if name.endswith(".py") and prog.match(name):
            modules.append(loadSource(os.path.join(root, name)))
  else:   # actually for jython compatibility
    os.path.walk(folder, walkModules, (modules, pattern))

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
      a_splitted = a[0].split(";")
      if len(a_splitted) != 1:
        # means we have pattern to match against
        if a_splitted[0].endswith("/"):
          debug("/ from folder " + a_splitted[0] + ". Use pattern: " + a_splitted[1])
          modules = loadModulesFromFolderRec(a_splitted[0], a_splitted[1])
      else:
        if a[0].endswith("/"):
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
