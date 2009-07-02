import sys
from pycharm.tcmessages import TeamcityServiceMessages

messages = TeamcityServiceMessages(prepend_linebreak=True)

def pytest_collectstart(collector):
    url = "file:///" + str(collector.fspath).replace("\\", "/")
    messages.testSuiteStarted(collector.name, location=url)

def pytest_runtest_makereport(item, call):
    if call.when == "setup":
        fspath, lineno, msg = item.reportinfo()
        url = "file:///" + str(fspath).replace("\\", "/")
        if lineno: url += ":" + str(lineno)
        messages.testStarted(item.name, location=url)

def pytest_runtest_logreport(rep):
    if rep.failed:
        messages.testFailed(rep.item.name, details=rep.longrepr)
    messages.testFinished(rep.item.name)

def pytest_collectreport(rep):
    messages.testSuiteFinished(rep.collector.name)
