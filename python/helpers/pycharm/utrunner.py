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
def loadSource(fileName):
  baseName = os.path.basename(fileName)
  moduleName = os.path.splitext(baseName)[0]
  if moduleName in modules: # add unique number to prevent name collisions
    cnt = 2
    while moduleName + str(cnt) in modules:
      cnt += 1
    moduleName += str(cnt)    
  debug("/ Loading " + fileName + " as " + moduleName)
  module = imp.load_source(moduleName, fileName)
  modules[moduleName] = module
  return module

testLoader = unittest.TestLoader()

all = unittest.TestSuite()
for arg in sys.argv[1:]:
  arg = arg.strip()
  if len(arg) == 0:
    continue

  a = arg.split("::")
  module = loadSource(a[0])
  if len(a) == 1:
    # From module
    debug("/ from module " + a[0])
    all.addTests(testLoader.loadTestsFromModule(module))
  elif len(a) == 2:
    # From testcase
    debug("/ from testcase " + a[1] + " in " + a[0])
    all.addTests(testLoader.loadTestsFromTestCase(getattr(module, a[1])))
  else:
    # From method in testcase
    debug("/ from method " + a[2] + " in testcase " +  a[1] + " in " + a[0])
    testCaseClass = getattr(module, a[1])
    all.addTest(testCaseClass(a[2]))

debug("/ Loaded " + str(all.countTestCases()) + " tests")
TeamcityServiceMessages(sys.stdout).testCount(all.countTestCases())
TeamcityTestRunner().run(all)
