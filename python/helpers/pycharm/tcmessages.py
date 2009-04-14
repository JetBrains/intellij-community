import sys

class TeamcityServiceMessages:
    quote = {"'": "|'", "|": "||", "\n": "|n", "\r": "|r", ']': '|]'}
    
    def __init__(self, output=sys.stdout):
        self.output = output
    
    def escapeValue(self, value):
        return "".join([self.quote.get(x, x) for x in value])
    
    def message(self, messageName, **properties):
        self.output.write("\n##teamcity[" + messageName)
        for k, v in properties.items():
            self.output.write(" %s='%s'" % (k, self.escapeValue(v)))
        self.output.write("]\n")
        
    def testSuiteStarted(self, suiteName):
        self.message('testSuiteStarted', name=suiteName)

    def testSuiteFinished(self, suiteName):
        self.message('testSuiteFinished', name=suiteName)

    def testStarted(self, testName):
        self.message('testStarted', name=testName)

    def testFinished(self, testName):
        self.message('testFinished', name=testName)
    
    def testIgnored(self, testName, message=''):
        self.message('testIgnored', name=testName, message=message)
        
    def testFailed(self, testName, message='', details=''):
        self.message('testFailed', name=testName,
            message=message, details=details)
        
    def testStdOut(self, testName, out):
        self.message('testStdOut', name=testName, out=out)
        
    def testStdErr(self, testName, out):
        self.message('testStdErr', name=testName, out=out)
