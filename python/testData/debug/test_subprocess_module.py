from __future__ import print_function
import os
import subprocess
import sys

ret = subprocess.call([os.path.abspath(sys.executable), "-m", "test_subprocess"])

print("Module returned code %d" % ret)
