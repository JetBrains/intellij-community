import os
import sys

args = [sys.executable, b'test4.py']
os.execv(args[0], args)
