import os
import sys
import unittest

try:
    from _pydev_bundle import pydev_monkey
except ImportError:
    sys.path.append(os.path.dirname(os.path.dirname(__file__)))
    from _pydev_bundle import pydev_monkey

from pydevd import SetupHolder
from _pydev_bundle.pydev_monkey import pydev_src_dir
from _pydevd_bundle.pydevd_command_line_handling import get_pydevd_file


class TestCase(unittest.TestCase):
    def setUp(self):
        self._original = SetupHolder.setup

    def tearDown(self):
        SetupHolder.setup = self._original

    def test_monkey(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = '''C:\\bin\\python.exe -u -c connect(\\"127.0.0.1\\")'''
        debug_command = (
            'import sys; '
            'sys.path.append(r\'%s\'); '
            "import pydevd; pydevd.settrace(host='127.0.0.1', port=0, suspend=False, "
            'trace_only_current_thread=False, patch_multiprocessing=True); '
            ''
            "from pydevd import SetupHolder; "
            "SetupHolder.setup = %s; "
            ''
            'connect("127.0.0.1")') % (pydev_src_dir, SetupHolder.setup)
        if sys.platform == "win32":
            debug_command = debug_command.replace('"', '\\"')
            debug_command = '"%s"' % debug_command

        self.assertEqual(
            'C:\\bin\\python.exe -u -c %s' % debug_command,
            pydev_monkey.patch_arg_str_win(check))

    def test_str_to_args_windows(self):
        self.assertEqual(['a', 'b'], pydev_monkey.str_to_args_windows('a "b"'))
        self.assertEqual(['foo', 'bar'], pydev_monkey.str_to_args_windows('foo bar'))
        self.assertEqual(['foo bar'], pydev_monkey.str_to_args_windows('"foo bar"'))
        self.assertEqual(['foo"bar'], pydev_monkey.str_to_args_windows('"foo""bar"'))
        self.assertEqual(['foo\\"bar'],
                         pydev_monkey.str_to_args_windows('"foo\\\\\\"bar"'))
        self.assertEqual(['foo\\bar'], pydev_monkey.str_to_args_windows('foo\\bar'))
        self.assertEqual(['arg one', 'arg two'],
                         pydev_monkey.str_to_args_windows('"arg one" "arg two"'))
        # A string surrounded by double quote marks is interpreted as a single argument, whether it contains whitespace characters or not
        # self.assertEqual([''], pydev_monkey.str_to_args_windows('""'))
        self.assertEqual(['arg'], pydev_monkey.str_to_args_windows('   "arg"   '))
        self.assertEqual(['one', 'two three', 'four'],
                         pydev_monkey.str_to_args_windows('one "two three" four'))
        # The double quote mark is interpreted as an escape sequence by the remaining backslash, causing a literal double quote mark (") to be placed in argv.
        # Within a quoted string, a pair of double quote marks is interpreted as a single escaped double quote mark.
        # self.assertEqual(['a"b"c'], pydev_monkey.str_to_args_windows('"a""b""c"'))

    def test_monkey_patch_args_indc(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', '-u', '-c', 'connect("127.0.0.1")']
        debug_command = (
            'import sys; sys.path.append(r\'%s\'); import pydevd; '
            'pydevd.settrace(host=\'127.0.0.1\', port=0, suspend=False, trace_only_current_thread=False, patch_multiprocessing=True); '
            ''
            "from pydevd import SetupHolder; "
            "SetupHolder.setup = %s; "
            ''
            'connect("127.0.0.1")') % (pydev_src_dir, SetupHolder.setup)
        if sys.platform == "win32":
            debug_command = debug_command.replace('"', '\\"')
            debug_command = '"%s"' % debug_command
        res = pydev_monkey.patch_args(check)
        self.assertEqual(res, [
            'C:\\bin\\python.exe',
            '-u',
            '-c',
            debug_command
        ])

    def test_monkey_patch_args_module(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0', 'multiprocess': True}
        check = ['C:\\bin\\python.exe', '-m', 'test']
        from _pydevd_bundle.pydevd_command_line_handling import get_pydevd_file
        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            get_pydevd_file(),
            '--module',
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--multiprocess',
            '--file',
            'test',
        ])

    # PY-60819
    @unittest.skipIf(sys.version_info < (3,),
                     "Test skipped on Python versions less than 3")
    def test_monkey_patch_args_quotes_managed_path_windows(self):
        from unittest.mock import patch

        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\Python\\python.exe',
            '"C:/path with spaces/pydevd.py"',]

        with patch.object(pydev_monkey, 'is_python', return_value=True), \
                patch('sys.platform', 'win32'):
            expected = [
                'C:\\Python\\python.exe',
                '"C:/path with spaces/pydevd.py"',
            ]
            actual = pydev_monkey.patch_args(check)
            self.assertEqual(expected, actual)

    def test_monkey_patch_args_no_indc(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', 'connect(\\"127.0.0.1\\")']
        from _pydevd_bundle.pydevd_command_line_handling import get_pydevd_file
        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'connect(\\"127.0.0.1\\")'])

    def test_monkey_patch_args_no_indc_with_pydevd(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', 'pydevd.py', 'connect(\\"127.0.0.1\\")', 'bar']

        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe', 'pydevd.py', 'connect(\\"127.0.0.1\\")', 'bar'])

    def test_monkey_patch_args_no_indc_without_pydevd(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', 'target.py', 'connect(\\"127.0.0.1\\")', 'bar']

        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'target.py',
            'connect(\\"127.0.0.1\\")',
            'bar',
        ])

    def test_monkey_patch_c_program_arg(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', '-u', 'target.py', '-c', '-another_arg']

        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            '-u',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'target.py',
            '-c',
            '-another_arg'
        ])

    def test_monkey_patch_x_interpreter_arg(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', '-X', 'pycache_prefix=C:\\temp', '-u',
                 'target.py', '-c', '-another_arg']
        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            '-X',
            'pycache_prefix=C:\\temp',
            '-u',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'target.py',
            '-c',
            '-another_arg'
        ])

    def test_monkey_patch_glued_x_interpreter_arg(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['/usr/bin/python', '-Xpycache_prefix=/tmp/cpython-cache', 'manage.py',
                 'runserver', 'localhost:8000']
        self.assertEqual(pydev_monkey.patch_args(check), [
            '/usr/bin/python',
            '-Xpycache_prefix=/tmp/cpython-cache',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'manage.py',
            'runserver',
            'localhost:8000'
        ])

    def test_monkey_patch_x_program_arg(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', '-u', 'target.py', '-c', '-X', 'foo=bar',
                 '-another_arg']
        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            '-u',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'target.py',
            '-c',
            '-X',
            'foo=bar',
            '-another_arg'
        ])

    def test_monkey_patch_b_interpreter_arg(self):
        SetupHolder.setup = {'client': '127.0.0.1', 'port': '0'}
        check = ['C:\\bin\\python.exe', '-B', '-u', 'target.py', '-c',
                 '-another_arg']
        self.assertEqual(pydev_monkey.patch_args(check), [
            'C:\\bin\\python.exe',
            '-B',
            '-u',
            get_pydevd_file(),
            '--port',
            '0',
            '--client',
            '127.0.0.1',
            '--file',
            'target.py',
            '-c',
            '-another_arg'
        ])


if __name__ == '__main__':
    unittest.main()
