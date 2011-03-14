# encoding: utf-8
# this code must work under pythons 2.2 through 3.0

import sys
import os
import re

version = (
  (sys.hexversion & (0xff << 24)) >> 24,
  (sys.hexversion & (0xff << 16)) >> 16
)

def sortedNoCase(p_array):
  "Sort an array case insensitevely, returns a sorted copy"
  p_array = list(p_array)    
  if version[0] < 3:
    def c(x, y):
      x = x.upper()
      y = y.upper()
      if x > y:
        return 1
      elif x < y:
        return -1
      else:
        return 0
    p_array.sort(c)
  else:
    p_array.sort(key=lambda x: x.upper())
    
  return p_array

def is_binary(path, f):
    suffixes = ('.so', '.pyd')
    for suf in suffixes:
      if f.endswith(suf):
        return True
    if f.endswith('.pyc') or f.endswith('.pyo'):
      fullname = os.path.join(path, f[:-1])
      return not os.path.exists(fullname)
    return False

logging = False
def note(*strings):
    if logging:
        for s in strings:
            sys.stderr.write(s)
        sys.stderr.write("\n")

mac_stdlib_pattern = re.compile("/System/Library/Frameworks/Python\\.framework/Versions/(.+)/lib/python\\1/(.+)")
mac_skip_modules = ["test", "ctypes/test", "distutils/tests", "email/test",
                    "importlib/test", "json/tests", "lib2to3/tests",
                    "sqlite3/test", "tkinter/test", "idlelib"]

def is_mac_skipped_module(path, f):
    fullname = os.path.join(path, f)
    m = mac_stdlib_pattern.match(fullname)
    if not m: return 0
    relpath = m.group(2)
    for module in mac_skip_modules:
        if relpath.startswith(module): return 1
    return 0

def find_binaries(paths):
  """
  Finds binaries in the given list of paths. 
  Understands nested paths, as sys.paths have it (both "a/b" and "a/b/c").
  Tries to be case-insensitive, but case-preserving.
  @param paths a list of paths.
  @return a list like [(module_name: full_path),.. ]
  """
  SEP = os.path.sep
  res = {} # {name.upper(): (name, full_path)}
  if not paths:
    return {}
  if hasattr(os, "java"): # jython can't have binary modules
    return {} 
  paths = sortedNoCase(paths)
  for path in paths:
    for root, dirs, files in os.walk(path):
      if root.endswith('__pycache__'): continue
      cutpoint = path.rfind(SEP)
      if cutpoint > 0:
        preprefix = path[(cutpoint + len(SEP)):] + '.'
      else:
        preprefix = ''
      prefix = root[(len(path) + len(SEP)):].replace(SEP, '.')
      if prefix:
        prefix += '.'
      note("root:", root, "path:", path, "prefix:", prefix, "preprefix:", preprefix)
      for f in files:
        if is_binary(root, f) and not is_mac_skipped_module(root, f):
          name = f[:f.rindex('.')]
          note("cutout:", name)
          if preprefix:
            note("prefixes: ", prefix, preprefix)
            pre_name = (preprefix + prefix + name).upper()
            if pre_name in res:
              res.pop(pre_name) # there might be a dupe, if paths got both a/b and a/b/c
            note("done with ", name)
          the_name = prefix + name
          res[the_name.upper()] = (the_name, root + SEP + f)
  return list(res.values())


# command-line interface
if __name__ == "__main__":
  from getopt import getopt

  helptext="""Finds binary importable python modules.
  Usage:
    find_binaries.py [-l] [dir ...]
    find_binaries.py -h
  Every "dir" will be non-recursively searched for binary modules (.so, .pyd).
  The list of full found modules is printed to stdout in the following format:
    module_namme <space> full paths to the binary file <newline>
  On filesystems that don't honour case properly, module_name may have a wrong
  case. Python import should be able to cope with this, though.
  If no dirs are given. sys.path will be the list of dirs.
    -v verbose: print some log messages to stderr.
    -h print this text.
  """
  opts, dirs = getopt(sys.argv[1:], "hl")
  opts = dict(opts)
  if '-h' in opts:
    print(helptext)
    sys.exit(0)
      
  logging = '-v' in opts

  if not dirs:
    dirs = sys.path
  
  for name, path in find_binaries(dirs):
    sys.stdout.write(name)
    sys.stdout.write(" ")
    sys.stdout.write(path)
    sys.stdout.write("\n")
    
