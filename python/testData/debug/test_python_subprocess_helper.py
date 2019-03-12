import subprocess
import sys


def foo():
    subprocess.call([sys.executable, '-c', "from test_python_subprocess_another_helper import boo"],
                    stderr=subprocess.PIPE)
    return 42

foo()
