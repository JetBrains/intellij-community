# Expects two env variables:
# PYCHARM_ENGULF_SCRIPT = which script should be engulfed.
# PYCHARM_PREPEND_SYSPATH = which entries should be added to the beginning of sys.path;
#     items must be separated by path separator. May be unset.
#
# Given script is loaded and compiled, then sys.path is prepended as requested.
# On win32, getpass is changed to insecure but working version.
# Then the compiled script evaluated, as if it were run by python interpreter itself.
# Works OK with debugger.

import os
import sys

target = os.getenv("PYCHARM_ENGULF_SCRIPT")
print("Running script through buildout: " + target)

assert target, "PYCHARM_ENGULF_SCRIPT must be set"

filepath = os.path.abspath(target)
f = None
try:
  f = open(filepath, "r")
  source = "\n".join((s.rstrip() for s in f.readlines()))
finally:
  if f:
    f.close()

from fix_getpass import fixGetpass
fixGetpass()

#prependable = os.getenv("PYCHARM_PREPEND_SYSPATH")
#if prependable:
#  sys.path[0:0] = [x for x in prependable.split(os.path.pathsep)]

# include engulfed's path, everyone expects this
our_path = os.path.dirname(filepath)
if our_path not in sys.path:
  sys.path.append(our_path)

compile(source, target, "exec")
exec(source)

# here we come
