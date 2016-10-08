import sys


class TeamcityServiceMessages:
    quote = {"'": "|'", "|": "||", "\n": "|n", "\r": "|r", ']': '|]', '[': '|['}

    def __init__(self, output=sys.stdout, prepend_linebreak=False):
        self.output = output
        self.prepend_linebreak = prepend_linebreak
        self.test_stack = []
        """
        Names of tests
        """
        self.topmost_suite = None
        """
        Last suite we entered in
        """

        self.number_of_tests = 0

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
        self.test_stack.append(suiteName)
        self.topmost_suite = suiteName

    def testSuiteFinished(self, suiteName):
        self.message('testSuiteFinished', name=suiteName)
        self.__pop_current_test()

    def testStarted(self, testName, location=None):
        self.message('testStarted', name=testName, locationHint=location)
        self.test_stack.append(testName)
        self.number_of_tests = self.number_of_tests + 1


    def testFinished(self, testName, duration=None):
        self.message('testFinished', name=testName, duration=duration)
        self.__pop_current_test()



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


    def __pop_current_test(self):
        try:
            self.test_stack.pop()
        except IndexError:
            pass

    def testError(self, testName, message='', details='', duration=None):
        self.message('testFailed', name=testName, message=message, details=details, error="true")
        self.testFinished(testName, int(duration) if duration else None)


    def current_test_name(self):
        """
        :return: name of current test we are in
        """
        return self.test_stack[-1] if len(self.test_stack) > 0 else None

    def testStdOut(self, testName, out):
        self.message('testStdOut', name=testName, out=out)

    def testStdErr(self, testName, out):
        self.message('testStdErr', name=testName, out=out)

    def testCount(self, count):
        self.message('testCount', count=count)

    def testMatrixEntered(self):
        self.message('enteredTheMatrix')
