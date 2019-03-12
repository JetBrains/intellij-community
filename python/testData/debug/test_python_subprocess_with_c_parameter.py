from __future__ import print_function
import subprocess
import sys

ret = subprocess.call([sys.executable, '-c', "from test_python_subprocess_helper import foo"], stderr=subprocess.PIPE)
print('The subprocess return code is %d' % ret)
