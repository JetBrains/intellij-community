import sys
sys.stderr.write("Warning: You are using test runners in legacy mode\n. "
                 "That means you have 'python.tests.enableUniversalTests=false' in registry.\n"
                 "This mode will be dropped in 2021. Consider removing this entry from registry and migrating to new test runners")
has_pytest = False
#there is the difference between 1.3.4 and 2.0.2 versions
#Since version 1.4, the testing tool "py.test" is part of its own pytest distribution.
try:
  import pytest
  has_pytest = True
except:
  try:
    import py
  except:
    raise NameError("No pytest runner found in selected interpreter")

def get_plugin_manager():
  try:
    from _pytest.config import get_plugin_manager
    return get_plugin_manager()
  except ImportError:
    from _pytest.core import PluginManager
    return PluginManager(load=True)

# "-s" is always required: no test output provided otherwise (see PY-12621)
args = sys.argv[1:]
args.append("-s") if "-s" not in args else None

if has_pytest:
  _preinit = []
  def main():
    _pluginmanager = get_plugin_manager()
    hook = _pluginmanager.hook
    try:
      config = hook.pytest_cmdline_parse(
              pluginmanager=_pluginmanager, args=args)
      exitstatus = hook.pytest_cmdline_main(config=config)
    except pytest.UsageError:
      e = sys.exc_info()[1]
      sys.stderr.write("ERROR: %s\n" %(e.args[0],))
      exitstatus = 3
    return exitstatus

else:
  def main():
    config = py.test.config
    try:
      config.parse(args)
      config.pluginmanager.do_configure(config)
      session = config.initsession()
      colitems = config.getinitialnodes()
      exitstatus = session.main(colitems)
      config.pluginmanager.do_unconfigure(config)
    except config.Error:
      e = sys.exc_info()[1]
      sys.stderr.write("ERROR: %s\n" %(e.args[0],))
      exitstatus = 3
    py.test.config = py.test.config.__class__()
    return exitstatus

if __name__ == "__main__":
  main()
