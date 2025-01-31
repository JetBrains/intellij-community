from __future__ import print_function
import os
import subprocess

ret = subprocess.call([os.path.abspath('test_executable_script_debug_helper.py')], shell=True, stderr=subprocess.PIPE)

print("Subprocess exited with return code: %d" % ret)
