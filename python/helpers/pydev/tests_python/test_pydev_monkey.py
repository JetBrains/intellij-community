import unittest
import pydev_monkey
import sys
from pydevd import SetupHolder
from pydev_monkey import pydev_src_dir



class TestCase(unittest.TestCase):

    def test_monkey(self):
        original = SetupHolder.setup
        
        try:
            SetupHolder.setup = {'client':'127.0.0.1', 'port': '0'}
            check='''C:\\bin\\python.exe -u -c "
connect(\\"127.0.0.1\\")
"'''
            sys.original_argv = []
            self.assertEqual(
                '"C:\\bin\\python.exe" "-u" "-c" "import sys; '
                'sys.path.append(r\'%s\'); '
                'import pydevd; pydevd.settrace(host=\'127.0.0.1\', port=0, suspend=False, '
                    'trace_only_current_thread=False, patch_multiprocessing=True); '
                    '\nconnect(\\"127.0.0.1\\")\n"' % pydev_src_dir, 
                pydev_monkey.patch_arg_str_win(check)
            )
        finally:
            SetupHolder.setup = original

    def test_str_to_args_windows(self):
        
        self.assertEqual(['a', 'b'], pydev_monkey.str_to_args_windows('a "b"'))
        
if __name__ == '__main__':
    unittest.main()