from __future__ import print_function
import os
import subprocess
import sys


ret = subprocess.call([os.path.abspath(sys.executable), "-I", "-m", "test_python_subprocess_another_helper"])
print("Module returned code %d" % ret)
