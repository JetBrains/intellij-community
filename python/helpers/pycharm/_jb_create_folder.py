"""
Accepts folder, creates (if does not exist) it and checks it is writable.
Empty output if ok. Error in stderr otherwise
"""
import os
import sys

folder = sys.argv[1]

d = os.path.dirname(folder)
if not os.path.exists(folder):
    os.makedirs(folder)

if not os.access(folder, os.W_OK):
    raise Exception("Dir {0} is not writable".format(folder))