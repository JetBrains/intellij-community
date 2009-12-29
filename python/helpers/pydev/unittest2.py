#!/usr/bin/env python
from unittest import TestResult

class TestListener:
    """ Simulate a Java interface by providing an abstract class
        All methods need to be implemented by classes extending TestListener
    """

    # Test was successful.
    def addSuccess(self, test):
        raise NotImplementedError, "TestListener.addSuccess()"
    
    # An error occured
    def addError(self, test, err):
        raise NotImplementedError, "TestListener.addError()"

    # A failure occurred.
    def addFailure(self, test, err):
        raise NotImplementedError, "TestListener.addFailure()"
        
    # A test started.
    def startTest(self, test):
        raise NotImplementedError, "TestListener.startTest()"

    # A test ended.
    def endTest(self, test):
        raise NotImplementedError, "TestListener.endTest()"

class TestResultWithListeners(TestResult):
    def __init__(self):
        TestResult.__init__(self)
        self.listeners = []
    
    def startTest(self, test):
        TestResult.startTest(self, test)
        for listener in self.listeners:
            listener.startTest(test)
            
    def endTest(self, test):
        for listener in self.listeners:
            listener.endTest(test)

    def addSuccess(self, test):
        TestResult.addSuccess(self, test)
        for listener in self.listeners:
            listener.addSuccess(test)

    def addError(self, test, err):
        TestResult.addError(self, test, err)
        for listener in self.listeners:
            listener.addError(test, err)

    def addFailure(self, test, err):
        TestResult.addFailure(self, test, err)
        for listener in self.listeners:
            listener.addFailure(test, err)
            
    def addListener(self, listener):
        self.listeners.append(listener)

    def removeListener(self, listener):
        self.listeners.remove(listener)
    
