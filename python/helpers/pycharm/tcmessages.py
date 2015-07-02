import sys


class TeamcityServiceMessages:
    quote = {"'": "|'", "|": "||", "\n": "|n", "\r": "|r", ']': '|]'}

    def __init__(self, output=sys.stdout, prepend_linebreak=False):
        self.output = output
        self.prepend_linebreak = prepend_linebreak

    def escapeValue(self, value):
        if sys.version_info[0] <= 2 and isinstance(value, unicode):
            s = value.encode("utf-8")
        else:
            s = str(value)
        return "".join([self.quote.get(x, x) for x in s])

    def message(self, messageName, **properties):
        s = "##teamcity[" + messageName
        for k, v in properties.items():
            if v is None:
                continue
            s = s + " %s='%s'" % (k, self.escapeValue(v))
        s += "]\n"

        if self.prepend_linebreak: self.output.write("\n")
        self.output.write(s)

    def testSuiteStarted(self, suiteName, location=None):
        self.message('testSuiteStarted', name=suiteName, locationHint=location)

    def testSuiteFinished(self, suiteName):
        self.message('testSuiteFinished', name=suiteName)

    def testStarted(self, testName, location=None):
        self.message('testStarted', name=testName, locationHint=location)

    def testFinished(self, testName, duration=None):
        self.message('testFinished', name=testName, duration=duration)

    def testIgnored(self, testName, message=''):
        self.message('testIgnored', name=testName, message=message)
        self.testFinished(testName)


    def testFailed(self, testName, message='', details='', expected='', actual='', duration=None):
        """
        Marks test as failed. *CAUTION*: This method calls ``testFinished``, so you do not need
        to call it second time. Try to provide ``duration`` if possible.

        """
        if expected and actual:
            self.message('testFailed', type='comparisonFailure', name=testName, message=message,
                         details=details, expected=expected, actual=actual)
        else:
            self.message('testFailed', name=testName, message=message, details=details)
        self.testFinished(testName, int(duration) if duration else None)

    def testError(self, testName, message='', details='', duration=None):
        self.message('testFailed', name=testName, message=message, details=details, error="true")
        self.testFinished(testName, int(duration) if duration else None)

    def testStdOut(self, testName, out):
        self.message('testStdOut', name=testName, out=out)

    def testStdErr(self, testName, out):
        self.message('testStdErr', name=testName, out=out)

    def testCount(self, count):
        self.message('testCount', count=count)

    def testMatrixEntered(self):
        self.message('enteredTheMatrix')
