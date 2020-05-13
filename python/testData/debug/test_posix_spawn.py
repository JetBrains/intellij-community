import os
import sys

pid = os.posix_spawn(sys.executable, [sys.executable, "test2.py"], os.environ)

pid, status = os.waitpid(pid, 0)
print(pid, status)
