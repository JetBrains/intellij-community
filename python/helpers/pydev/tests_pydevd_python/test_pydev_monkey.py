import sys
import os
import unittest
try:
    from _pydev_bundle import pydev_monkey
except:
    sys.path.append(os.path.dirname(os.path.dirname(__file__)))
    from _pydev_bundle import pydev_monkey
from pydevd import SetupHolder
from _pydev_bundle.pydev_monkey import pydev_src_dir



class TestCase(unittest.TestCase):

    def test_monkey(self):
        original = SetupHolder.setup

        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check='''C:\\bin\\python.exe -u -c connect(\\"127.0.0.1\\")'''
            sys.original_argv = []
            debug_command = (
                'import sys; '
                'sys.path.append(r\'%s\'); '
                'import pydevd; pydevd.settrace(host=\'127.0.0.1\', port=0, suspend=False, '
                'trace_only_current_thread=False, patch_multiprocessing=True); '
                'sys.original_argv = []; '
                'connect("127.0.0.1")') % pydev_src_dir
            if sys.platform == "win32":
                debug_command = debug_command.replace('"', '\\"')
                debug_command = '"%s"' % debug_command
            self.assertEqual(
                'C:\\bin\\python.exe -u -c %s' % debug_command,
                pydev_monkey.patch_arg_str_win(check))
        finally:
            SetupHolder.setup = original

    def test_str_to_args_windows(self):
        self.assertEqual(['a', 'b'], pydev_monkey.str_to_args_windows('a "b"'))

    def test_monkey_patch_args_indc(self):
        original = SetupHolder.setup

        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check=['C:\\bin\\python.exe', '-u', '-c', 'connect("127.0.0.1")']
            sys.original_argv = []
            debug_command = (
                'import sys; sys.path.append(r\'%s\'); import pydevd; '
                'pydevd.settrace(host=\'127.0.0.1\', port=0, suspend=False, trace_only_current_thread=False, patch_multiprocessing=True); '
                'sys.original_argv = []; '
                'connect("127.0.0.1")') % pydev_src_dir
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
        finally:
            SetupHolder.setup = original

    def test_monkey_patch_args_module(self):
        original = SetupHolder.setup

        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check=['C:\\bin\\python.exe', '-m', 'test']
            sys.original_argv = ['pydevd', '--multiprocess']
            self.assertEqual(pydev_monkey.patch_args(check), [
                'C:\\bin\\python.exe',
                'pydevd',
                '--module',
                '--multiprocess',
                'test',
            ])
        finally:
            SetupHolder.setup = original

    def test_monkey_patch_args_no_indc(self):
        original = SetupHolder.setup

        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check=['C:\\bin\\python.exe', 'connect(\\"127.0.0.1\\")']
            sys.original_argv = ['my', 'original', 'argv']
            self.assertEqual(pydev_monkey.patch_args(check), [
                'C:\\bin\\python.exe', 'my', 'original', 'argv', 'connect(\\"127.0.0.1\\")'])
        finally:
            SetupHolder.setup = original

    def test_monkey_patch_args_no_indc_with_pydevd(self):
        original = SetupHolder.setup

        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check=['C:\\bin\\python.exe', 'pydevd.py', 'connect(\\"127.0.0.1\\")', 'bar']
            sys.original_argv = ['my', 'original', 'argv']

            self.assertEqual(pydev_monkey.patch_args(check), [
                'C:\\bin\\python.exe', 'pydevd.py', 'connect(\\"127.0.0.1\\")', 'bar'])
        finally:
            SetupHolder.setup = original

    def test_monkey_patch_args_no_indc_without_pydevd(self):
        original = SetupHolder.setup

        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check=['C:\\bin\\python.exe', 'target.py', 'connect(\\"127.0.0.1\\")', 'bar']
            sys.original_argv = ['pydevd.py', '--a=1', 'b', '--c=2', '--file', 'ignore_this.py']

            self.assertEqual(pydev_monkey.patch_args(check), [
                'C:\\bin\\python.exe',
                'pydevd.py',
                '--a=1',
                'b',
                '--c=2',
                '--file',
                'target.py',
                'connect(\\"127.0.0.1\\")',
                'bar',
            ])
        finally:
            SetupHolder.setup = original

if __name__ == '__main__':
    unittest.main()