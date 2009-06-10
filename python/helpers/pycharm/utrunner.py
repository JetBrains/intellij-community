import unittest
import os
import imp
import sys
import types
from pycharm.tcmessages import TeamcityServiceMessages
from pycharm.tcunittest import TeamcityTestRunner

ENABLE_DEBUG_LOGGING = False
if os.getenv("UTRUNNER_ENABLE_DEBUG_LOGGING"):
  ENABLE_DEBUG_LOGGING = True

def debug(what):
  if ENABLE_DEBUG_LOGGING:
    print >>sys.stdout, what

modules = {}
def getModuleName(prefix, cnt):
  return prefix + "%" + str(cnt)

def loadSource(fileName):
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

def walkModules(modules, dirname, names):
  for name in names:
    if name.endswith(".py"):
      modules.append(loadSource(os.path.join(dirname, name)))

def loadModulesFromFolderRec(folder):
  modules = []
  os.path.walk(folder, walkModules, modules)
  return modules

testLoader = unittest.TestLoader()

all = unittest.TestSuite()
for arg in sys.argv[1:]:
  arg = arg.strip()
  if len(arg) == 0:
    continue

  a = arg.split("::")
  if len(a) == 1:
    # From module or folder
    if a[0].endswith("/"):
      debug("/ from folder " + a[0])
      modules = loadModulesFromFolderRec(a[0])
    else:
      debug("/ from module " + a[0])
      modules = [loadSource(a[0])]

    for module in modules:
      all.addTests(testLoader.loadTestsFromModule(module)._tests)
  elif len(a) == 2:
    # From testcase
    debug("/ from testcase " + a[1] + " in " + a[0])
    module = loadSource(a[0])
    all.addTests(testLoader.loadTestsFromTestCase(getattr(module, a[1]))._tests)
  else:
    # From method in testcase
    debug("/ from method " + a[2] + " in testcase " +  a[1] + " in " + a[0])
    module = loadSource(a[0])
    testCaseClass = getattr(module, a[1])
    all.addTest(testCaseClass(a[2]))

debug("/ Loaded " + str(all.countTestCases()) + " tests")
TeamcityServiceMessages(sys.stdout).testCount(all.countTestCases())
TeamcityTestRunner().run(all)
