import sys

has_pytest = False
#there is the difference between 1.3.4 and 2.0.2 versions
#Since version 1.4, the testing tool “py.test” is part of its own pytest distribution.
try:
  import pytest
  has_pytest = True
except:
  try:
    import py
  except:
    raise NameError("No py.test runner found in selected interpreter")

if has_pytest:
  _preinit = []
  from _pytest.core import PluginManager
  def main():
    args = sys.argv[1:]
    _pluginmanager = PluginManager(load=True)
    hook = _pluginmanager.hook
    try:
      config = hook.pytest_cmdline_parse(
              pluginmanager=_pluginmanager, args=args)
      exitstatus = hook.pytest_cmdline_main(config=config)
    except UsageError:
      e = sys.exc_info()[1]
      sys.stderr.write("ERROR: %s\n" %(e.args[0],))
      exitstatus = 3
    return exitstatus

else:
  def main():
    args = sys.argv[1:]
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