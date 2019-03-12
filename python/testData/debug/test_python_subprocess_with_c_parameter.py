from __future__ import print_function
import subprocess
import sys

ret = subprocess.call([sys.executable, '-c', "'from __future__ import print_function; print(\"Hello, World\")'"],
                      stderr=subprocess.PIPE)
print('The subprocess return code is %d' % ret)
