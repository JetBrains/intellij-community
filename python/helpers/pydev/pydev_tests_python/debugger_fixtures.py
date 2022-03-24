from contextlib import contextmanager
import os
import threading

import pytest

from pydev_tests_python import debugger_unittest
from pydev_tests_python.debugger_unittest import get_free_port, overrides, IS_CPYTHON, IS_JYTHON, IS_IRONPYTHON, \
    IS_PY3K

import sys


def get_java_location():
    from java.lang import System  # @UnresolvedImport
    jre_dir = System.getProperty("java.home")
    for f in [os.path.join(jre_dir, 'bin', 'java.exe'), os.path.join(jre_dir, 'bin', 'java')]:
        if os.path.exists(f):
            return f
    raise RuntimeError('Unable to find java executable')


def get_jython_jar():
    from java.lang import ClassLoader  # @UnresolvedImport
    cl = ClassLoader.getSystemClassLoader()
    paths = map(lambda url: url.getFile(), cl.getURLs())
    for p in paths:
        if 'jython.jar' in p:
            return p
    raise RuntimeError('Unable to find jython.jar')


class _WriterThreadCaseMSwitch(debugger_unittest.AbstractWriterThread):

    TEST_FILE = 'pydev_tests_python.resources._debugger_case_m_switch'
    IS_MODULE = True

    @overrides(debugger_unittest.AbstractWriterThread.get_environ)
    def get_environ(self):
        env = os.environ.copy()
        curr_pythonpath = env.get('PYTHONPATH', '')

        root_dirname = os.path.dirname(os.path.dirname(__file__))

        curr_pythonpath += root_dirname + os.pathsep
        env['PYTHONPATH'] = curr_pythonpath
        return env

    @overrides(debugger_unittest.AbstractWriterThread.get_main_filename)
    def get_main_filename(self):
        return debugger_unittest._get_debugger_test_file('_debugger_case_m_switch.py')


class _WriterThreadCaseModuleWithEntryPoint(_WriterThreadCaseMSwitch):

    TEST_FILE = 'pydev_tests_python.resources._debugger_case_module_entry_point:main'
    IS_MODULE = True

    @overrides(_WriterThreadCaseMSwitch.get_main_filename)
    def get_main_filename(self):
        return debugger_unittest._get_debugger_test_file('_debugger_case_module_entry_point.py')


class AbstractWriterThreadCaseFlask(debugger_unittest.AbstractWriterThread):

    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = True
    FLASK_FOLDER = None

    TEST_FILE = 'flask'
    IS_MODULE = True

    def write_add_breakpoint_jinja2(self, line, func, template):
        '''
            @param line: starts at 1
        '''
        assert self.FLASK_FOLDER is not None
        breakpoint_id = self.next_breakpoint_id()
        template_file = debugger_unittest._get_debugger_test_file(os.path.join(self.FLASK_FOLDER, 'templates', template))
        self.write("111\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (self.next_seq(), breakpoint_id, 'jinja2-line', template_file, line, func))
        self.log.append('write_add_breakpoint_jinja: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id

    @overrides(debugger_unittest.AbstractWriterThread.get_environ)
    def get_environ(self):
        import platform

        env = os.environ.copy()
        env['FLASK_APP'] = 'app.py'
        env['FLASK_ENV'] = 'development'
        env['FLASK_DEBUG'] = '0'
        if platform.system() != 'Windows':
            locale = 'en_US.utf8' if platform.system() == 'Linux' else 'en_US.UTF-8'
            env.update({
                'LC_ALL': locale,
                'LANG': locale,
            })
        return env

    def get_cwd(self):
        return debugger_unittest._get_debugger_test_file(self.FLASK_FOLDER)

    def get_command_line_args(self):
        assert self.FLASK_FOLDER is not None
        free_port = get_free_port()
        self.flask_port = free_port
        return [
            'flask',
            'run',
             '--no-debugger',
             '--no-reload',
             '--with-threads',
            '--port',
            str(free_port),
        ]

    def _ignore_stderr_line(self, line):
        if debugger_unittest.AbstractWriterThread._ignore_stderr_line(self, line):
            return True

        if 'Running on http:' in line:
            return True

        if 'GET / HTTP/' in line:
            return True

        return False

    def create_request_thread(self):
        outer = self

        class T(threading.Thread):

            def run(self):
                try:
                    from urllib.request import urlopen
                except ImportError:
                    from urllib import urlopen
                for _ in range(10):
                    try:
                        stream = urlopen('http://127.0.0.1:%s' % (outer.flask_port,))
                        contents = stream.read()
                        if IS_PY3K:
                            contents = contents.decode('utf-8')
                        self.contents = contents
                        break
                    except IOError:
                        continue

        t = T()
        t.daemon = True
        return t


class AbstractWriterThreadCaseDjango(debugger_unittest.AbstractWriterThread):

    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = True
    DJANGO_FOLDER = None

    def _ignore_stderr_line(self, line):
        if debugger_unittest.AbstractWriterThread._ignore_stderr_line(self, line):
            return True

        if 'GET /my_app' in line:
            return True

        return False

    def get_command_line_args(self):
        assert self.DJANGO_FOLDER is not None
        free_port = get_free_port()
        self.django_port = free_port
        return [
            debugger_unittest._get_debugger_test_file(os.path.join(self.DJANGO_FOLDER, 'manage.py')),
            'runserver',
            '--noreload',
            str(free_port),
        ]

    def write_add_breakpoint_django(self, line, func, template):
        '''
            @param line: starts at 1
        '''
        assert self.DJANGO_FOLDER is not None
        breakpoint_id = self.next_breakpoint_id()
        template_file = debugger_unittest._get_debugger_test_file(os.path.join(self.DJANGO_FOLDER, 'my_app', 'templates', 'my_app', template))
        self.write("111\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (self.next_seq(), breakpoint_id, 'django-line', template_file, line, func))
        self.log.append('write_add_django_breakpoint: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id

    def create_request_thread(self, uri):
        outer = self

        class T(threading.Thread):

            def run(self):
                try:
                    from urllib.request import urlopen
                except ImportError:
                    from urllib import urlopen
                for _ in range(10):
                    try:
                        stream = urlopen('http://127.0.0.1:%s/%s' % (outer.django_port, uri))
                        contents = stream.read()
                        if IS_PY3K:
                            contents = contents.decode('utf-8')
                        self.contents = contents
                        break
                    except IOError:
                        continue

        t = T()
        t.daemon = True
        return t


class DebuggerRunnerSimple(debugger_unittest.DebuggerRunner):

    def get_command_line(self):
        if IS_JYTHON:
            if sys.executable is not None:
                # i.e.: we're running with the provided jython.exe
                return [sys.executable]
            else:

                return [
                    get_java_location(),
                    '-classpath',
                    get_jython_jar(),
                    'org.python.util.jython'
                ]

        if IS_CPYTHON:
            return [sys.executable, '-u']

        if IS_IRONPYTHON:
            return [
                    sys.executable,
                    '-X:Frames'
                ]

        raise RuntimeError('Unable to provide command line')


class DebuggerRunnerRemote(debugger_unittest.DebuggerRunner):

    def get_command_line(self):
        return [sys.executable, '-u']

    def add_command_line_args(self, args):
        writer = self.writer

        ret = args + [self.writer.TEST_FILE]
        ret = writer.update_command_line_args(ret)  # Provide a hook for the writer
        return ret


@pytest.fixture
def case_setup():

    runner = DebuggerRunnerSimple()

    class WriterThread(debugger_unittest.AbstractWriterThread):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(
                self,
                filename,
                **kwargs
            ):
            WriterThread.TEST_FILE = debugger_unittest._get_debugger_test_file(filename)
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)

            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_unhandled_exceptions(case_setup):

    original = case_setup.test_file

    def check_test_suceeded_msg(writer, stdout, stderr):
        return 'TEST SUCEEDED' in ''.join(stderr)

    def additional_output_checks(writer, stdout, stderr):
        # Don't call super as we have an expected exception
        if 'ValueError: TEST SUCEEDED' not in stderr:
            raise AssertionError('"ValueError: TEST SUCEEDED" not in stderr.\nstdout:\n%s\n\nstderr:\n%s' % (
                stdout, stderr))

    def test_file(*args, **kwargs):
        kwargs.setdefault('check_test_suceeded_msg', check_test_suceeded_msg)
        kwargs.setdefault('additional_output_checks', additional_output_checks)
        return original(*args, **kwargs)

    case_setup.test_file = test_file

    return case_setup


@pytest.fixture
def case_setup_remote():

    runner = DebuggerRunnerRemote()

    class WriterThread(debugger_unittest.AbstractWriterThread):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(
                self,
                filename,
                **kwargs
            ):

            def update_command_line_args(writer, args):
                ret = debugger_unittest.AbstractWriterThread.update_command_line_args(writer, args)
                ret.append(str(writer.port))
                return ret

            WriterThread.TEST_FILE = debugger_unittest._get_debugger_test_file(filename)
            WriterThread.update_command_line_args = update_command_line_args
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)

            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_multiprocessing():

    runner = DebuggerRunnerSimple()

    class WriterThread(debugger_unittest.AbstractWriterThread):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(
                self,
                filename,
                **kwargs
            ):

            def update_command_line_args(writer, args):
                ret = debugger_unittest.AbstractWriterThread.update_command_line_args(writer, args)
                ret.insert(ret.index('--qt-support'), '--multiprocess')
                return ret

            WriterThread.update_command_line_args = update_command_line_args
            WriterThread.TEST_FILE = debugger_unittest._get_debugger_test_file(filename)
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)

            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_multiproc():

    runner = DebuggerRunnerSimple()

    class WriterThread(debugger_unittest.AbstractDispatcherThread):
        def all_finished_ok(self):
            for w in self.writers:
                w.finished_ok = True
            self.finished_ok = True

    class CaseSetup(object):

        @contextmanager
        def test_file(
                self,
                filename,
                **kwargs
        ):

            def update_command_line_args(writer, args):
                ret = debugger_unittest.AbstractWriterThread.update_command_line_args(writer, args)
                ret.insert(ret.index('--qt-support'), '--multiproc')
                return ret

            WriterThread.update_command_line_args = update_command_line_args
            WriterThread.TEST_FILE = debugger_unittest._get_debugger_test_file(filename)
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)

            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_m_switch():

    runner = DebuggerRunnerSimple()

    class WriterThread(_WriterThreadCaseMSwitch):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(self, **kwargs):
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)
            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_m_switch_entry_point():

    runner = DebuggerRunnerSimple()

    class WriterThread(_WriterThreadCaseModuleWithEntryPoint):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(self, **kwargs):
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)
            with runner.check_case(WriterThread) as writer:

                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_django():

    runner = DebuggerRunnerSimple()

    class WriterThread(AbstractWriterThreadCaseDjango):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(self, **kwargs):
            import django
            version = [int(x) for x in django.get_version().split('.')][:2]
            if version == [1, 7]:
                django_folder = 'my_django_proj_17'
            elif version == [2, 1]:
                django_folder = 'my_django_proj_21'
            else:
                raise AssertionError('Can only check django 1.7 and 2.1 right now. Found: %s' % (version,))

            WriterThread.DJANGO_FOLDER = django_folder
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)

            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()


@pytest.fixture
def case_setup_flask():

    runner = DebuggerRunnerSimple()

    class WriterThread(AbstractWriterThreadCaseFlask):
        pass

    class CaseSetup(object):

        @contextmanager
        def test_file(self, **kwargs):
            WriterThread.FLASK_FOLDER = 'flask1'
            for key, value in kwargs.items():
                assert hasattr(WriterThread, key)
                setattr(WriterThread, key, value)

            with runner.check_case(WriterThread) as writer:
                yield writer

    return CaseSetup()
