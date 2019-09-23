from __future__ import print_function
import os
import subprocess
import sys


ret = subprocess.call([os.path.abspath(sys.executable), "test_python_subprocess_another_helper.py"])

print("The subprocess finished with the return code %d." % ret)