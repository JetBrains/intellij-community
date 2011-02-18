import sys, datetime
from tcunittest import strclass

try:
  from nose.config import Config
  from nose.plugins.multiprocess import MultiProcessTestRunner
except:
  raise NameError("Please, install nosetests")

PYTHON_VERSION_MAJOR = sys.version_info[0]
Process = Queue = Pool = Event = None

def _import_mp():
    global Process, Queue, Pool, Event
    try:
        from multiprocessing import Process as Process_, \
            Queue as Queue_, Pool as Pool_, Event as Event_
        Process, Queue, Pool, Event = Process_, Queue_, Pool_, Event_
    except ImportError:
      raise NameError("multiprocessing module is not available, multiprocess plugin "
             "cannot be used. Try to upgrade python.")

from nose_utils import TeamcityNoseTestResult

class MultiProcessNoseTestResult(TeamcityNoseTestResult):
    def __init__(self, stream, descriptions, verbosity, config=None,
                 errorClasses=None):
        TeamcityNoseTestResult.__init__(self, stream, descriptions, verbosity, config,
                 errorClasses)

    def startTest(self, test):
        location, suite_location = self.__getSuite(test)
        setattr(test, "startTime", datetime.datetime.now())
        self.messages.testStarted(self.getTestName(test), location=location)

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
              location = "file://"+self.test_address(test.test.descriptor)+":"+str(func_lineno(test.test.descriptor))
            else:
              location = "file://"+self.test_address(test.test.test)+":"+str(func_lineno(test.test.test))
          except:
              location = "python_uttestid://" + str(test.id())
        return (location, suite_location)


class MultiProcessTeamcityNoseRunner(MultiProcessTestRunner):
    """Test runner that supports teamcity output
    """
    def __init__(self, stream=sys.stderr, descriptions=1, verbosity=1,
                 config=None, processes=0, timeout=10):
        if config is None:
            config = Config()
        self.config = config
        _import_mp()
        self.stream=stream
        self.descriptions=descriptions
        self.verbosity=verbosity
        self.config.multiprocess_workers = int(processes)
        self.config.multiprocess_timeout = int(timeout)
        from nose.loader import defaultTestLoader
        self.loaderClass = defaultTestLoader

    def _makeResult(self):
        return MultiProcessNoseTestResult(self.stream,
                              self.descriptions,
                              self.verbosity,
                              self.config)


    def run(self, test):
        wrapper = self.config.plugins.prepareTest(test)
        if wrapper is not None:
            test = wrapper

        # plugins can decorate or capture the output stream
        wrapped = self.config.plugins.setOutputStream(self.stream)
        if wrapped is not None:
            self.stream = wrapped

        testQueue = Queue()
        resultQueue = Queue()
        tasks = {}
        completed = {}
        workers = []
        to_teardown = []
        shouldStop = Event()

        result = self._makeResult()

        for case in self.nextBatch(test):
            from nose.case import Test
            from nose.failure import Failure
            from nose.suite import ContextSuite
            if (isinstance(case, Test) and
                isinstance(case.test, Failure)):
                case(result) # run here to capture the failure
                continue
            # handle shared fixtures
            if isinstance(case, ContextSuite) and self.sharedFixtures(case):
                try:
                    case.setUp()
                except (KeyboardInterrupt, SystemExit):
                    raise
                except:
                    result.addError(case, sys.exc_info())
                else:
                    to_teardown.append(case)
                    for _t in case:
                        test_addr = self.address(_t)
                        testQueue.put(test_addr, block=False)
                        tasks[test_addr] = None

            else:
                test_addr = self.address(case)
                testQueue.put(test_addr, block=False)
                tasks[test_addr] = None
        for i in range(self.config.multiprocess_workers):
            from nose.plugins.multiprocess import runner
            import pickle
            p = Process(target=runner, args=(i,
                                             testQueue,
                                             resultQueue,
                                             shouldStop,
                                             self.loaderClass,
                                             result.__class__,
                                             pickle.dumps(self.config)))
            p.start()
            workers.append(p)

        if PYTHON_VERSION_MAJOR > 2:
            from queue import Empty
        else:
            from Queue import Empty
        while tasks:
            try:
                addr, batch_result = resultQueue.get(
                    timeout=self.config.multiprocess_timeout)
                try:
                    tasks.pop(addr)
                except KeyError:
                    pass
                else:
                    completed[addr] = batch_result
                self.consolidate(result, batch_result)
                if (self.config.stopOnError
                    and not result.wasSuccessful()):
                    # set the stop condition
                    shouldStop.set()
                    break
            except Empty:
                any_alive = False
                for w in workers:
                    if w.is_alive():
                        any_alive = True
                        break
                if not any_alive:
                    break

        for case in to_teardown:
            try:
                case.tearDown()
            except (KeyboardInterrupt, SystemExit):
                raise
            except:
                result.addError(case, sys.exc_info())

        # Tell all workers to stop
        for w in workers:
            if w.is_alive():
                testQueue.put('STOP', block=False)
        return result
