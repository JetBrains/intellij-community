from pycharm.tcmessages import TeamcityServiceMessages
import os

messages = TeamcityServiceMessages(prepend_linebreak=True)
messages.testMatrixEntered()
try:
  import pytest
  PYVERSION = [int(x) for x in pytest.__version__.split(".")]
except:
  import py
  PYVERSION = [int(x) for x in py.__version__.split(".")]

def get_name(nodeid):
    return nodeid.split("::")[-1]

if PYVERSION > [1,3,4]:
  items = {}
  current_suite = None
  current_file = None
  current_file_suite = None

  def pytest_runtest_logstart(nodeid, location):
    path = "file://" + os.path.realpath(location[0]) + ":" + str(location[1] + 1)
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
        messages.testSuiteStarted(current_file_suite, location="file://" + os.path.realpath(location[0]))

    if location[2].find(".") != -1:
      suite = location[2].split(".")[0]
      name = location[2].split(".")[-1]
    else:
      name = location[2]
      splitted = nodeid.split("::")
      ind = splitted.index(name.split("[")[0])
      if splitted[ind-1] == current_file:
        suite = None
      else:
        suite = current_suite
    if suite != current_suite:
      if current_suite:
        messages.testSuiteFinished(current_suite)
      current_suite = suite
      if current_suite:
        messages.testSuiteStarted(current_suite, location="file://" + os.path.realpath(location[0]))
    messages.testStarted(name, location=path)
    items[nodeid] = name

  def pytest_runtest_logreport(report):
    name = items[report.nodeid]
    if report.failed:
      messages.testFailed(name, details=report.longrepr)
    elif report.skipped:
      messages.testIgnored(name)
    messages.testFinished(name)

  def pytest_sessionfinish(session, exitstatus):
    if current_suite:
      messages.testSuiteFinished(current_suite)
    if current_file_suite:
      messages.testSuiteFinished(current_file_suite)

else:
  def fspath_to_url(fspath):
    return "file:///" + str(fspath).replace("\\", "/")

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
