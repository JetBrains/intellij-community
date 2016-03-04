import debugger_unittest
import sys
import re
import os

CHECK_BASELINE, CHECK_REGULAR, CHECK_CYTHON = 'baseline', 'regular', 'cython'

class PerformanceWriterThread(debugger_unittest.AbstractWriterThread):

    CHECK = None

    debugger_unittest.AbstractWriterThread.get_environ # overrides
    def get_environ(self):
        env = os.environ.copy()
        if self.CHECK == CHECK_BASELINE:
            env['PYTHONPATH'] = r'X:\PyDev.Debugger.baseline'
        elif self.CHECK == CHECK_CYTHON:
            env['PYDEVD_USE_CYTHON'] = 'YES'
        elif self.CHECK == CHECK_REGULAR:
            env['PYDEVD_USE_CYTHON'] = 'NO'
        else:
            raise AssertionError("Don't know what to check.")
        return env

    debugger_unittest.AbstractWriterThread.get_pydevd_file # overrides
    def get_pydevd_file(self):
        if self.CHECK == CHECK_BASELINE:
            return os.path.abspath(os.path.join(r'X:\PyDev.Debugger.baseline', 'pydevd.py'))
        dirname = os.path.dirname(__file__)
        dirname = os.path.dirname(dirname)
        return os.path.abspath(os.path.join(dirname, 'pydevd.py'))


class WriterThreadPerformance1(PerformanceWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_performance_1.py')
    BENCHMARK_NAME = 'method_calls_with_breakpoint'

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(17, 'method')
        self.write_make_initial_run()
        self.finished_ok = True

class WriterThreadPerformance2(PerformanceWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_performance_1.py')
    BENCHMARK_NAME = 'method_calls_without_breakpoint'

    def run(self):
        self.start_socket()
        self.write_make_initial_run()
        self.finished_ok = True

class WriterThreadPerformance3(PerformanceWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_performance_1.py')
    BENCHMARK_NAME = 'method_calls_with_step_over'

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(26, None)

        self.write_make_initial_run()
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        self.write_step_over(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        self.write_run_thread(thread_id)
        self.finished_ok = True

class WriterThreadPerformance4(PerformanceWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_performance_1.py')
    BENCHMARK_NAME = 'method_calls_with_exception_breakpoint'

    def run(self):
        self.start_socket()
        self.write_add_exception_breakpoint('ValueError')

        self.write_make_initial_run()
        self.finished_ok = True


class CheckDebuggerPerformance(debugger_unittest.DebuggerRunner):

    def get_command_line(self):
        return [sys.executable]

    def _get_time_from_result(self, result):
        stdout = ''.join(result['stdout'])
        match = re.search('TotalTime>>((\d|\.)+)<<', stdout)
        time_taken = match.group(1)
        return float(time_taken)

    def obtain_results(self, writer_thread_class):
        time_when_debugged = self._get_time_from_result(self.check_case(writer_thread_class))

        args = self.get_command_line()
        args.append(writer_thread_class.TEST_FILE)
        regular_time = self._get_time_from_result(self.run_process(args, writer_thread=None))
        simple_trace_time = self._get_time_from_result(self.run_process(args+['--regular-trace'], writer_thread=None))
        print(writer_thread_class.BENCHMARK_NAME, time_when_debugged, regular_time, simple_trace_time)

        if 'SPEEDTIN_AUTHORIZATION_KEY' in os.environ:

            SPEEDTIN_AUTHORIZATION_KEY = os.environ['SPEEDTIN_AUTHORIZATION_KEY']

            # sys.path.append(r'X:\speedtin\pyspeedtin')
            import pyspeedtin # If the authorization key is there, pyspeedtin must be available
            import pydevd
            pydevd_cython_project_id, pydevd_pure_python_project_id = 6, 7
            if writer_thread_class.CHECK == CHECK_BASELINE:
                project_ids = (pydevd_cython_project_id, pydevd_pure_python_project_id)
            elif writer_thread_class.CHECK == CHECK_REGULAR:
                project_ids = (pydevd_pure_python_project_id,)
            elif writer_thread_class.CHECK == CHECK_CYTHON:
                project_ids = (pydevd_cython_project_id,)
            else:
                raise AssertionError('Wrong check: %s' % (writer_thread_class.CHECK))
            for project_id in project_ids:
                api = pyspeedtin.PySpeedTinApi(authorization_key=SPEEDTIN_AUTHORIZATION_KEY, project_id=project_id)

                benchmark_name = writer_thread_class.BENCHMARK_NAME

                if writer_thread_class.CHECK == CHECK_BASELINE:
                    version = '0.0.1_baseline'
                    return # No longer commit the baseline (it's immutable right now).
                else:
                    version=pydevd.__version__,

                commit_id, branch, commit_date = api.git_commit_id_branch_and_date_from_path(pydevd.__file__)
                api.add_benchmark(benchmark_name)
                api.add_measurement(
                        benchmark_name,
                        value=time_when_debugged,
                        version=version,
                        released=False,
                        branch=branch,
                        commit_id=commit_id,
                        commit_date=commit_date,
                )
                api.commit()


    def check_performance1(self):
        self.obtain_results(WriterThreadPerformance1)

    def check_performance2(self):
        self.obtain_results(WriterThreadPerformance2)

    def check_performance3(self):
        self.obtain_results(WriterThreadPerformance3)

    def check_performance4(self):
        self.obtain_results(WriterThreadPerformance4)

if __name__ == '__main__':
    debugger_unittest.SHOW_WRITES_AND_READS = False
    debugger_unittest.SHOW_OTHER_DEBUG_INFO = False
    debugger_unittest.SHOW_STDOUT = False

    for check in (
            # CHECK_BASELINE, -- Checks against the version checked out at X:\PyDev.Debugger.baseline.
            CHECK_REGULAR,
            CHECK_CYTHON
    ):
        PerformanceWriterThread.CHECK = check
        print('Checking: %s' % (check,))
        check_debugger_performance = CheckDebuggerPerformance()
        check_debugger_performance.check_performance1()
        check_debugger_performance.check_performance2()
        check_debugger_performance.check_performance3()
        check_debugger_performance.check_performance4()
