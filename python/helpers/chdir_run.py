import os
import sys


if __name__ == '__main__':
  working_dir = sys.argv[1]

  os.chdir(working_dir)
  sys.argv[1] = sys.executable
  os.execv(sys.executable, sys.argv[1:])
