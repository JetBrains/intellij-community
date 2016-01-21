import sys
import os

helpers_dir = os.getenv("PYCHARM_HELPERS_DIR", sys.path[0])
if sys.path[0] != helpers_dir:
    sys.path.insert(0, helpers_dir)

from nose_utils import TeamcityPlugin

from pycharm_run_utils import debug, import_system_module
from pycharm_run_utils import adjust_sys_path

adjust_sys_path(False)

shlex = import_system_module("shlex")

try:
  from nose.core import TestProgram
  from nose.config import Config
  from nose.plugins.manager import DefaultPluginManager
except:
  raise NameError("Please, install nosetests")

teamcity_plugin = TeamcityPlugin()

class MyConfig(Config):
  def __init__(self, **kw):
    super(MyConfig, self).__init__(**kw)

  def __setstate__(self, state):
    super(MyConfig, self).__setstate__(state)
    self.plugins.addPlugin(teamcity_plugin)

def process_args():
  tests = []

  opts = None
  if sys.argv[-1].startswith("-"):
    test_names = sys.argv[1:-1]
    opts = sys.argv[-1]
  else:
    test_names = sys.argv[1:]

  for arg in test_names:
    arg = arg.strip()
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

  argv.extend(tests)


  if opts:
    options = shlex.split(opts)
    argv.extend(options)

  manager = DefaultPluginManager()
  manager.addPlugin(teamcity_plugin)
  config = MyConfig(plugins=manager)
  config.configure(argv)

  TestProgram(argv=argv, config=config, exit=False)

if __name__ == "__main__":
  process_args()