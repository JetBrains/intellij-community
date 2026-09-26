import os
import sys

args = [sys.executable, b'test4.py']
os.spawnv(os.P_WAIT, args[0], args)
