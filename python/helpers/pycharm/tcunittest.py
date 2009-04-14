import traceback, types, sys, os
from unittest import TestResult

from pycharm.tcmessages import TeamcityServiceMessages

class TeamcityTestResult(TestResult):
    def __init__(self, stream=sys.stdout):
        TestResult.__init__(self)

        self.output = stream
        
        self.createMessages()

    def createMessages(self):
        self.messages = TeamcityServiceMessages(self.output)
    
    def formatErr(self, err):
        exctype, value, tb = err
        return ''.join(traceback.format_exception(exctype, value, tb))
    
    def getTestName(self, test):
        return test.shortDescription() or str(test)

    def addSuccess(self, test):
        TestResult.addSuccess(self, test)
        
        self.output.write("ok\n")
        
    def addError(self, test, err):
        TestResult.addError(self, test, err)
        
        err = self.formatErr(err)
        
        self.messages.testFailed(self.getTestName(test),
            message='Error', details=err)
            
    def addFailure(self, test, err):
        TestResult.addFailure(self, test, err)

        err = self.formatErr(err)
        
        self.messages.testFailed(self.getTestName(test),
            message='Failure', details=err)

    def startTest(self, test):
        self.messages.testStarted(self.getTestName(test))
        
    def stopTest(self, test):
        self.messages.testFinished(self.getTestName(test))

class TeamcityTestRunner:
    def __init__(self, stream=sys.stderr):
        self.stream = stream

    def _makeResult(self):
        return TeamcityTestResult(self.stream)

    def run(self, test):
        result = self._makeResult()
        test(result)
        return result
