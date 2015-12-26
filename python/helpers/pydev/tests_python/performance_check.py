import debugger_unittest
import sys
import re
import os
import pydevd


class WriterThreadPerformance1(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_performance_1.py')
    BENCHMARK_NAME = 'method_calls_with_breakpoint'

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(17, 'method')
        self.write_make_initial_run()
        self.finished_ok = True

class WriterThreadPerformance2(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_performance_1.py')
    BENCHMARK_NAME = 'method_calls_without_breakpoint'

    def run(self):
        self.start_socket()
        self.write_make_initial_run()
        self.finished_ok = True

class WriterThreadPerformance3(debugger_unittest.AbstractWriterThread):

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

class WriterThreadPerformance4(debugger_unittest.AbstractWriterThread):

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

        if 'SPEEDTIN_AUTHORIZATION_KEY' in os.environ and 'SPEEDTIN_PROJECT_ID_REGULAR' in os.environ \
            and 'SPEEDTIN_PROJECT_ID_NOTRACE' in os.environ and 'SPEEDTIN_PROJECT_ID_SIMPLETRACE' in os.environ:

            SPEEDTIN_AUTHORIZATION_KEY = os.environ['SPEEDTIN_AUTHORIZATION_KEY']
            SPEEDTIN_PROJECT_ID_REGULAR = os.environ['SPEEDTIN_PROJECT_ID_REGULAR']
            SPEEDTIN_PROJECT_ID_NOTRACE = os.environ['SPEEDTIN_PROJECT_ID_NOTRACE']
            SPEEDTIN_PROJECT_ID_SIMPLETRACE = os.environ['SPEEDTIN_PROJECT_ID_SIMPLETRACE']

            # Upload data to https://www.speedtin.com
            for project_id, value in (
                (SPEEDTIN_PROJECT_ID_REGULAR, time_when_debugged),
                (SPEEDTIN_PROJECT_ID_NOTRACE, regular_time),
                (SPEEDTIN_PROJECT_ID_SIMPLETRACE, simple_trace_time),
                ):
                try:
                    import pyspeedtin
                except ImportError:
                    continue
                api = pyspeedtin.PySpeedTinApi(authorization_key=SPEEDTIN_AUTHORIZATION_KEY, project_id=project_id)
                api.add_benchmark(writer_thread_class.BENCHMARK_NAME)
                commit_id, branch, commit_date = api.git_commit_id_branch_and_date_from_path(__file__)
                api.add_measurement(
                    writer_thread_class.BENCHMARK_NAME,
                    value=value, # How many times slower than without debugging
                    version=pydevd.__version__,
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

    check_debugger_performance = CheckDebuggerPerformance()
    check_debugger_performance.check_performance1()
    check_debugger_performance.check_performance2()
    check_debugger_performance.check_performance3()
    check_debugger_performance.check_performance4()
