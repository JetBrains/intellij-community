import os
import sys
helpers_dir = os.getenv("PYCHARM_HELPERS_DIR", sys.path[0])
if sys.path[0] != helpers_dir:
    sys.path.insert(0, helpers_dir)

from tcmessages import TeamcityServiceMessages
from pycharm_run_utils import adjust_sys_path

adjust_sys_path(False)

# Directory where test script exist
CURRENT_DIR_NAME = ""
if sys.argv:
  last_arg = sys.argv[-1]

  if os.path.isfile(last_arg):
    CURRENT_DIR_NAME = os.path.dirname(last_arg)
  else:
    CURRENT_DIR_NAME = last_arg
    if not str(last_arg).endswith(os.sep):
      CURRENT_DIR_NAME = last_arg + os.sep

messages = TeamcityServiceMessages(prepend_linebreak=True)
if not "_jb_do_not_call_enter_matrix" in os.environ:
  messages.testMatrixEntered()
try:
  import pytest
  PYVERSION = [int(x) for x in pytest.__version__.split(".")]
except:
  import py
  PYVERSION = [int(x) for x in py.__version__.split(".")]

def get_name(nodeid):
  return nodeid.split("::")[-1]

def fspath_to_url(fspath):
  return "file:///" + str(fspath).replace("\\", "/")

if PYVERSION > [1, 4, 0]:
  items = {}
  current_suite = None
  current_file = None
  current_file_suite = None

  def pytest_collection_finish(session):
    messages.testCount(len(session.items))

  def pytest_runtest_logstart(nodeid, location):
    path = "file://" + os.path.realpath(os.path.join(CURRENT_DIR_NAME, location[0]))
    if location[1]:
      path += ":" +str(location[1] + 1)
    global current_suite, current_file, current_file_suite
    current_file = nodeid.split("::")[0]

    file_suite = current_file.split("/")[-1]
    if file_suite != current_file_suite:
      if current_suite:
        messages.testSuiteFinished(current_suite)
      if current_file_suite:
        messages.testSuiteFinished(current_file_suite)
      current_file_suite = file_suite
      if current_file_suite:
        messages.testSuiteStarted(current_file_suite, location=path)

    if location[2].find(".") != -1:
      suite = location[2].split(".")[0]
      name = location[2].split(".")[-1]
    else:
      name = location[2]
      splitted = nodeid.split("::")
      try:
        ind = splitted.index(name.split("[")[0])
      except ValueError:
        try:
          ind = splitted.index(name)
        except ValueError:
          ind = 0
      if splitted[ind-1] == current_file:
        suite = None
      else:
        suite = current_suite
    if suite != current_suite:
      if current_suite:
        messages.testSuiteFinished(current_suite)
      current_suite = suite
      if current_suite:
        messages.testSuiteStarted(current_suite, location=path)
    messages.testStarted(name, location=path)
    items[nodeid] = name

  def pytest_runtest_logreport(report):
    name = items[report.nodeid]

    if report.skipped:
      messages.testIgnored(name)
    elif report.failed: # Duration should be in ms, but report has s
      messages.testFailed(name, details=report.longrepr, duration=int(report.duration * 1000))
    elif report.when == "call":
      messages.testFinished(name, duration=int(report.duration * 1000))

  def pytest_sessionfinish(session, exitstatus):
    if not messages.number_of_tests and not current_suite and not current_file_suite:
      messages.testError("ERROR", "No tests found")
    if current_suite:
      messages.testSuiteFinished(current_suite)
    if current_file_suite:
      messages.testSuiteFinished(current_file_suite)

  from _pytest.terminal import TerminalReporter
  class PycharmTestReporter(TerminalReporter):
    def __init__(self, config, file=None):
      TerminalReporter.__init__(self, config, file)

    def summary_errors(self):
      reports = self.getreports('error')
      if not reports:
        return
      for rep in self.stats['error']:
        name = rep.nodeid.split("/")[-1]
        location = None
        if hasattr(rep, 'location'):
          location, lineno, domain = rep.location

        messages.testSuiteStarted(name, location=fspath_to_url(location))
        messages.testStarted("ERROR", location=fspath_to_url(location))
        TerminalReporter.summary_errors(self)
        messages.testError("ERROR")
        messages.testSuiteFinished(name)

else:
  def pytest_collectstart(collector):
    if collector.name != "()":
      messages.testSuiteStarted(collector.name, location=fspath_to_url(collector.fspath))

  def pytest_runtest_makereport(item, call):
    if call.when == "setup":
      fspath, lineno, msg = item.reportinfo()
      url = fspath_to_url(fspath)
      if lineno: url += ":" + str(lineno)
    #    messages.testStarted(item.name, location=url)

  def pytest_runtest_logreport(report):
    if report.item._args:
      name = report.item.function.__name__ + str(report.item._args)
    else:
      name = report.item.name
    if report.failed:
      messages.testFailed(name, details=report.longrepr)
    elif report.skipped:
      messages.testIgnored(name)
    else:
      messages.testFinished(name)

  def pytest_collectreport(report):
    if report.collector.name != "()":
      messages.testSuiteFinished(report.collector.name)

  def pytest_itemstart(item, node=None):
    if item._args:
      name = item.function.__name__ + str(item._args)
    else:
      name = item.name
    if hasattr(item, "_fslineno"):
      path = fspath_to_url(item._fslineno[0]) + ":" + str(item._fslineno[1] + 1)
    else:
      path = fspath_to_url(item.fspath)
    messages.testStarted(name, location=path)


try:
  @pytest.hookimpl(trylast=True)
  def pytest_configure(config):
    reporter = PycharmTestReporter(config, sys.stdout)
    config.pluginmanager.unregister(name="terminalreporter")
    config.pluginmanager.register(reporter, 'terminalreporter')
except AttributeError as e:
  sys.stderr.write("Unable to set hookimpl. Some errors may be ignored. Make sure you use PyTest 2.8.0+. Error was {0}".format(e))