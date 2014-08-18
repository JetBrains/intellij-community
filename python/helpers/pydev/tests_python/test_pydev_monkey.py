import unittest
import pydev_monkey
import sys



class TestCase(unittest.TestCase):

    def test_monkey(self):
        check='''C:\\bin\\python.exe -u -c "
connect(\\"127.0.0.1\\")
"'''
        sys.original_argv = []
        self.assertEqual('"-u" "-c" "\nconnect(\\"127.0.0.1\\")\n"', pydev_monkey.patch_arg_str_win(check))

    def test_str_to_args_windows(self):
        
        self.assertEqual(['a', 'b'], pydev_monkey.str_to_args_windows('a "b"'))
        
if __name__ == '__main__':
    unittest.main()