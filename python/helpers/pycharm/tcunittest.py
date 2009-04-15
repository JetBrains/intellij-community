import traceback, types, sys, os
from unittest import TestResult
import datetime

from pycharm.tcmessages import TeamcityServiceMessages

class TeamcityTestResult(TestResult):
    def __init__(self, stream=sys.stdout):
        TestResult.__init__(self)

        self.output = stream
        self.messages = TeamcityServiceMessages(self.output)
    
    def formatErr(self, err):
        exctype, value, tb = err
        return ''.join(traceback.format_exception(exctype, value, tb))
    
    def getTestName(self, test):
        return str(test)

    def getTestId(self, test):
        return test.id

    def addSuccess(self, test):
        TestResult.addSuccess(self, test)
        
        # self.output.write("ok\n")
        
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
        setattr(test, "startTime", datetime.datetime.now())
        self.messages.testStarted(self.getTestName(test), location="python_uttestid://" + str(test.id()))
        
    def stopTest(self, test):
        start = getattr(test, "startTime", datetime.datetime.now())
        d = datetime.datetime.now() - start
        duration=d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
        self.messages.testFinished(self.getTestName(test), duration=duration)

class TeamcityTestRunner:
    def __init__(self, stream=sys.stdout):
        self.stream = stream

    def _makeResult(self):
        return TeamcityTestResult(self.stream)

    def run(self, test):
        result = self._makeResult()
        test(result)
        return result
