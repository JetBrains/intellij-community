import sys
from pycharm.tcmessages import TeamcityServiceMessages

messages = TeamcityServiceMessages(prepend_linebreak=True)

def fspath_to_url(fspath):
    return "file:///" + str(fspath).replace("\\", "/")

def pytest_collectstart(collector):
    messages.testSuiteStarted(collector.name, location=fspath_to_url(collector.fspath))

def pytest_runtest_makereport(item, call):
    if call.when == "setup":
        fspath, lineno, msg = item.reportinfo()
        url = fspath_to_url(fspath)
        if lineno: url += ":" + str(lineno)
        messages.testStarted(item.name, location=url)

def pytest_runtest_logreport(rep):
    if rep.failed:
        messages.testFailed(rep.item.name, details=rep.longrepr)
    elif rep.skipped:
        messages.testIgnored(rep.item.name)
    messages.testFinished(rep.item.name)

def pytest_collectreport(rep):
    messages.testSuiteFinished(rep.collector.name)
