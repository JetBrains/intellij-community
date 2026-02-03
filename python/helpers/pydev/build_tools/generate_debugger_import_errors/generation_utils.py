#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import re
import sys
import os
import time
from contextlib import contextmanager

if sys.version_info[0] == 2:
    from StringIO import StringIO
else:
    from io import StringIO

from pydev_tests_python.debugger_unittest import DebuggerRunner, AbstractWriterThread, \
    start_in_daemon_thread, read_process, SHOW_OTHER_DEBUG_INFO, wait_for_condition

TIMEOUT = 2
TRIM_PATTERN = '('
TEMP_FILES_DIR = 'resources'
RESULT_FILE = 'error_lines.txt'
exception_types = ('ImportError', 'AttributeError', 'ModuleNotFoundError', 'NameError')
HELP_MODULES_START_STRING = 'Please wait a moment while I gather a list of all available modules...'
HELP_MODULES_END_STRING = 'Enter any module name to get more help.  Or, type "modules spam" to search for modules whose name or summary contain the string "spam".'
HELP_MODULES_END_STRING_PY2 = 'Enter any module name to get more help.  Or, type "modules spam" to search for modules whose descriptions contain the word "spam".'

temp_file_dir_path = os.path.join(os.path.dirname(__file__), TEMP_FILES_DIR)
resulting_dict = dict()


def get_builtin_module_path(file_name):
    try:
        r_path = os.path.realpath  # @UndefinedVariable
    except:
        r_path = os.path.abspath

    ret = os.path.normcase(r_path(os.path.join(os.path.dirname(__file__), file_name)))
    if not os.path.exists(ret):
        ret = os.path.join(os.path.dirname(ret), TEMP_FILES_DIR, os.path.basename(ret))
    if not os.path.exists(ret):
        raise AssertionError('Expected: %s to exist.' % (ret,))
    return ret


def create_temp_dir():
    try:
        os.mkdir(temp_file_dir_path)
    except:
        pass


def create_temp_file(file_name):
    open(os.path.join(temp_file_dir_path, file_name), 'w')


def clear_temp_dir():
    try:
        for file_name in os.listdir(temp_file_dir_path):
            if file_name.startswith("__py"):
                continue
            os.remove(os.path.join(temp_file_dir_path, file_name))
    except Exception as e:
        print(e)


def get_builtin_modules_name():
    old_stdout = sys.stdout
    result = StringIO()
    sys.stdout = result
    help('modules')
    sys.stdout = old_stdout
    result = result.getvalue().replace('\n\n', ' ').replace('\n', ' ')
    result = result.replace(HELP_MODULES_START_STRING, '')
    result = result.replace(HELP_MODULES_END_STRING, '')
    result = result.replace(HELP_MODULES_END_STRING_PY2, '')
    result = re.split("\s+", result)
    return list(filter(lambda s: len(s) > 0 and not s.startswith('pydev') and not s.startswith('_pydev'), result))


def update_environ():
    os.environ['PWD'] = temp_file_dir_path
    os.environ['PYTHONPATH'] = os.environ['PYTHONPATH'] + os.pathsep + temp_file_dir_path
    os.environ['IDE_PROJECT_ROOTS'] = temp_file_dir_path
    os.chdir(temp_file_dir_path)


def write_result_to_file():
    with open(os.path.join(os.path.dirname(__file__), RESULT_FILE), 'a') as file:
        for key, val in resulting_dict.items():
            file.write('"%s" to "%s",\n' % (key, val,))
    resulting_dict.clear()


def parse_output(file_name, err_massage):
    if err_massage is None:
        return
    lines = err_massage.splitlines()
    for line in lines:
        for exception_type in exception_types:
            if line.startswith(exception_type):
                ind = line.find(TRIM_PATTERN)
                if ind != -1:
                    line = line[:ind]
                line = line.strip()
                resulting_dict[line] = file_name


def debug_file(file_name):
    try:
        with generate_module_error_string().gen(file_name) as w:
            w.write_make_initial_run()
            w.finished_ok = True
            writer = w
        while not writer.stop_working:
            time.sleep(.2)
    except AssertionError:
        pass
    finally:
        if writer is not None:
            parse_output(file_name, writer.error_string)


class GenerationDebuggerRunner(DebuggerRunner):
    def get_command_line(self):
        return [sys.executable, '-u']

    def fail_with_message(self, msg, stdout, stderr, writerThread):
        raise AssertionError(msg)

    @contextmanager
    def check_case(self, writer_class):
        if callable(writer_class):
            writer = writer_class()
        else:
            writer = writer_class

        update_environ()

        try:
            writer.start()
            wait_for_condition(lambda: hasattr(writer, 'port'), timeout=TIMEOUT)

            self.writer = writer
            args = self.get_command_line()
            args = self.add_command_line_args(args)

            with self.run_process(args, writer) as dct_with_stdout_stderr:
                try:
                    wait_for_condition(lambda: writer.finished_initialization,
                                       timeout=TIMEOUT)
                except:
                    writer.finished_initialization = True
                finally:
                    writer.get_stdout = lambda: ''.join(dct_with_stdout_stderr['stdout'])
                    writer.get_stderr = lambda: ''.join(dct_with_stdout_stderr['stderr'])

                yield writer
        except:
            pass
        finally:
            writer.do_kill()
            writer.log = []

        stdout = dct_with_stdout_stderr['stdout']
        stderr = dct_with_stdout_stderr['stderr']
        writer.additional_output_checks(''.join(stdout), ''.join(stderr))

    @contextmanager
    def run_process(self, args, writer):
        process = self.create_process(args, writer)
        stdout = []
        stderr = []
        finish = [False]
        dct_with_stdout_stder = {}

        try:
            start_in_daemon_thread(read_process, (
                process.stdout, stdout, sys.stdout, 'stdout', finish))
            start_in_daemon_thread(read_process, (
                process.stderr, stderr, sys.stderr, 'stderr', finish))

            if SHOW_OTHER_DEBUG_INFO:
                print('Both processes started')

            initial_time = time.time()

            dct_with_stdout_stder['stdout'] = stdout
            dct_with_stdout_stder['stderr'] = stderr
            yield dct_with_stdout_stder

            if not writer.finished_ok:
                self.fail_with_message(
                    "The thread that was doing the tests didn't finish successfully.",
                    stdout, stderr, writer)

            while True:
                if process.poll() is not None:
                    break
                else:
                    if writer is not None:
                        if writer.FORCE_KILL_PROCESS_WHEN_FINISHED_OK:
                            process.kill()
                            continue
                        if time.time() - initial_time > TIMEOUT:  # timed out
                            process.kill()
                            break
                time.sleep(.2)
        except TimeoutError:
            writer.write_dump_threads()
            time.sleep(.2)
            raise
        finally:
            finish[0] = True


def generate_module_error_string():
    runner = GenerationDebuggerRunner()

    class WriterThread(AbstractWriterThread):
        def __init__(self, *args, **kwargs):
            AbstractWriterThread.__init__(self, *args, **kwargs)
            self.error_string = None
            self.stop_working = False

        def additional_output_checks(self, stdout, stderr):
            self.error_string = stderr
            self.stop_working = True

    class Generator(object):
        @contextmanager
        def gen(self, filename, **kwargs):
            WriterThread.TEST_FILE = get_builtin_module_path(filename)
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)
            try:
                with runner.check_case(WriterThread) as writer:
                    yield writer
            except:
                pass

    return Generator()
