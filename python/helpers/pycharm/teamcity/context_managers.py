try:
    from contextlib2 import contextmanager
except Exception:
    from contextlib import contextmanager


@contextmanager
def block(messages, name, flowId=None):
    messages.blockOpened(name, flowId)
    yield
    messages.blockClosed(name, flowId)


@contextmanager
def compilation(messages, compiler):
    messages.compilationStarted(compiler)
    yield
    messages.compilationFinished(compiler)


@contextmanager
def testSuite(messages, name):
    messages.testSuiteStarted(name)
    yield
    messages.testSuiteFinished(name)


@contextmanager
def test(messages, testName, captureStandardOutput=None, flowId=None, testDuration=None):
    messages.testStarted(testName=testName, captureStandardOutput=captureStandardOutput, flowId=flowId)
    yield
    messages.testFinished(testName=testName, testDuration=testDuration, flowId=flowId)


@contextmanager
def progress(messages, message):
    messages.progressStart(message)
    yield
    messages.progressFinish(message)


@contextmanager
def serviceMessagesDisabled(messages, flowId=None):
    messages.disableServiceMessages(flowId=flowId)
    yield
    messages.enableServiceMessages(flowId=flowId)


@contextmanager
def serviceMessagesEnabled(messages, flowId=None):
    messages.enableServiceMessages(flowId=flowId)
    yield
    messages.disableServiceMessages(flowId=flowId)
