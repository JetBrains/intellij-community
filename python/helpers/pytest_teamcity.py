import sys
from pycharm.tcmessages import TeamcityServiceMessages

messages = TeamcityServiceMessages(prepend_linebreak=True)

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
