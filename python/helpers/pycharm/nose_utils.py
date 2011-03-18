import unittest
from tcmessages import TeamcityServiceMessages
import sys, traceback, datetime
from tcunittest import strclass, TeamcityTestResult

try:
  from nose.config import Config
  from nose.result import TextTestResult
  from nose.util import isclass # backwards compat
except:
  raise NameError("Please, install nosetests")

class TeamcityNoseTestResult(TextTestResult, TeamcityTestResult):
    """TeamcityTestResult
    """
    def __init__(self, stream, descriptions, verbosity, config=None,
                 errorClasses=None):
        if errorClasses is None:
            errorClasses = {}
        self.errorClasses = errorClasses
        if config is None:
            config = Config()
        self.config = config
        self.output = stream
        self.messages = TeamcityServiceMessages(self.output, prepend_linebreak=True)
        self.messages.testMatrixEntered()
        self.current_suite = None
        TextTestResult.__init__(self, stream, descriptions, verbosity, config, errorClasses)
        TeamcityTestResult.__init__(self, stream)

    def addError(self, test, err):
        """as in nosetests
        """
        ec, ev, tb = err
        try:
            exc_info = self._exc_info_to_string(err, test)
        except TypeError:
            # 2.3 compat
            exc_info = self._exc_info_to_string(err)
        for cls, (storage, label, isfail) in self.errorClasses.items():
            #if 'Skip' in cls.__name__ or 'Skip' in ec.__name__:
            #    from nose.tools import set_trace
            #    set_trace()
            if isclass(ec) and issubclass(ec, cls):
                if isfail:
                    test.passed = False
                storage.append((test, exc_info))
                return
        self.errors.append((test, exc_info))
        test.passed = False

        err = self.formatErr(err)

        self.messages.testError(self.getTestName(test),
            message='Error', details=err)

    def is_gen(self, test):
        if hasattr(test.test, "descriptor"):
            if test.test.descriptor is not None:
                return True
        return False

    def getTestName(self, test):
        test_name_full = str(test)
        if self.is_gen(test):
            return test_name_full

        ind_1 = test_name_full.rfind('(')
        if ind_1 != -1:
          return test_name_full[:ind_1]
        ind = test_name_full.rfind('.')
        if ind != -1:
          return test_name_full[test_name_full.rfind(".") + 1:]
        return test_name_full

    def addSuccess(self, test):
        TextTestResult.addSuccess(self, test)

    def addFailure(self, test, err):
        err = self.formatErr(err)

        self.messages.testFailed(self.getTestName(test),
            message='Failure', details=err)

    def addSkip(self, test, reason):
        self.messages.testIgnored(self.getTestName(test), message=reason)

    def __getSuite(self, test):
        if hasattr(test, "suite"):
          suite = strclass(test.suite)
          suite_location = test.suite.location
          location = test.suite.abs_location
          if hasattr(test, "lineno"):
            location = location + ":" + str(test.lineno)
          else:
            location = location + ":" + str(test.test.lineno)
        else:
          suite = strclass(test.__class__)
          suite_location = "python_uttestid://" + suite
          try:
            from nose_helper.util import func_lineno

            if hasattr(test.test, "descriptor") and test.test.descriptor:
              suite_location = "file://"+self.test_address(test.test.descriptor)
              location = suite_location+":"+str(func_lineno(test.test.descriptor))
            else:
              suite_location = "file://"+self.test_address(test.test.test)
              location = "file://"+self.test_address(test.test.test)+":"+str(func_lineno(test.test.test))
          except:
            test_id = test.id()
            suite_id = test_id[:test_id.rfind(".")]
            suite_location = "python_uttestid://"+str(suite_id)
            location = "python_uttestid://" + str(test_id)
        return (location, suite_location)

    def test_address(self, test):
        if hasattr(test, "address"):
            return test.address()[0]
        t = type(test)
        file = None
        import types, os
        if (t == types.FunctionType or issubclass(t, type) or t == type
            or isclass(test)):
            module = getattr(test, '__module__', None)
            if module is not None:
                m = sys.modules[module]
                file = getattr(m, '__file__', None)
                if file is not None:
                    file = os.path.abspath(file)
            if file.endswith("pyc"):
              file = file[:-1]
            return file
        raise TypeError("I don't know what %s is (%s)" % (test, t))

    def getSuiteName(self, test):
        test_name_full = str(test)

        if self.is_gen(test):
            ind_1 = test_name_full.rfind('(')
            if ind_1 != -1:
                ind = test_name_full.rfind('.')
                if ind != -1:
                  return test_name_full[:test_name_full.rfind(".")]

        ind_1 = test_name_full.rfind('(')
        if ind_1 != -1:
          return test_name_full[ind_1+1: -1]
        ind = test_name_full.rfind('.')
        if ind != -1:
          return test_name_full[:test_name_full.rfind(".")]
        return test_name_full

    def startTest(self, test):
        location, suite_location = self.__getSuite(test)
        suite = self.getSuiteName(test)
        if suite != self.current_suite:
            if self.current_suite:
                self.messages.testSuiteFinished(self.current_suite)
            self.current_suite = suite
            self.messages.testSuiteStarted(self.current_suite, location=suite_location)
        setattr(test, "startTime", datetime.datetime.now())
        self.messages.testStarted(self.getTestName(test), location=location)

    def stopTest(self, test):
        start = getattr(test, "startTime", datetime.datetime.now())
        d = datetime.datetime.now() - start
        duration=d.microseconds / 1000 + d.seconds * 1000 + d.days * 86400000
        self.messages.testFinished(self.getTestName(test), duration=int(duration))

    def endLastSuite(self):
        if self.current_suite:
            self.messages.testSuiteFinished(self.current_suite)
            self.current_suite = None


class TeamcityNoseRunner(unittest.TextTestRunner):
    """Test runner that supports teamcity output
    """
    def __init__(self, stream=sys.stdout, descriptions=1, verbosity=1,
                 config=None):
        if config is None:
            config = Config()
        self.config = config

        unittest.TextTestRunner.__init__(self, stream, descriptions, verbosity)


    def _makeResult(self):
        return TeamcityNoseTestResult(self.stream,
                              self.descriptions,
                              self.verbosity,
                              self.config)

    def run(self, test):
        """Overrides to provide plugin hooks and defer all output to
        the test result class.
        """
        #for 2.5 compat
        for plugin in self.config.plugins.plugins:
            if plugin.name == "profile":
                plugin.begin()

        wrapper = self.config.plugins.prepareTest(test)
        if wrapper is not None:
            test = wrapper

        # plugins can decorate or capture the output stream
        wrapped = self.config.plugins.setOutputStream(self.stream)
        if wrapped is not None:
            self.stream = wrapped

        result = self._makeResult()
        test(result)
        result.endLastSuite()
        return result
