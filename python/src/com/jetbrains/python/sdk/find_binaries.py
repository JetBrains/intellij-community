# encoding: utf-8
# this code must work under pythons 2.2 through 3.0

import sys
import os


def sortedNoCase(p_array):
  def c(x, y):
    x = x.upper()
    y = y.upper()
    if x > y:
      return 1
    elif x < y:
      return -1
    else:
      return 0
  p_array = list(p_array)    
  p_array.sort(c)
  return p_array


def find_binaries(paths):
  """
  Finds binaries in the given list of paths. 
  Understands nested paths, as sys.paths have it (both "a/b" and "a/b/c").
  Tries to be case-insensitive, but case-preserving.
  @param paths a list of paths.
  @return a list like [(module_name: full_path),.. ]
  """
  SEP = os.path.sep
  suffixes = ('.so', '.pyd')
  res = {} # {name.upper(): (name, full_path)} 
  if not paths:
    return {}
  paths = sortedNoCase(paths)
  for path in paths:
    for root, dirs, files in os.walk(path):
      cutpoint = path.rfind(SEP)
      if cutpoint > 0:
        preprefix = path[(cutpoint + len(SEP)):] + '.'
      else:
        preprefix = ''
      prefix = root[(len(path) + len(SEP)):].replace(SEP, '.')
      if prefix:
        prefix += '.'
      #print root, path, prefix, preprefix # XXX
      for f in files:
        for suf in suffixes:
          if f.endswith(suf):
            name = f[:-len(suf)]
            #print "+++ ", name
            if preprefix:
              #print("prefixes: ", prefix, preprefix) # XXX
              pre_name = (preprefix + prefix + name).upper()
              if pre_name in res:
                res.pop(pre_name) # there might be a dupe, if paths got both a/b and a/b/c
              #print "+ ", name # XXX
            the_name = prefix + name   
            res[the_name.upper()] = (the_name, root + SEP + f)
            break
  return list(res.values())


# command-line interface
if __name__ == "__main__":
  from getopt import getopt

  helptext="""Finds binary importable python modules.
  Usage:
    find_binaries.py -h -- prints this message.
    find_binaries.py [dir ...]
  Every "dir" will be non-recursively searched for binary modules (.so, .pyd).
  The list of full found modules is printed to stdout in the following format:
    module_namme <space> full paths to the binary file <newline>
  On filesystems that don't hamour case properly, module_name may have a wrong 
  case. Python import should be able to cope with this, though.
  If no dirs are given. sys.path will be the list of dirs.
  """
  opts, dirs = getopt(sys.argv[1:], "h")
  opts = dict(opts)
  if '-h' in opts:
    print(helptext)
    sys.exit(0)
    
  if not dirs:
    dirs = sys.path
  
  for name, path in find_binaries(dirs):
    sys.stdout.write(name)
    sys.stdout.write(" ")
    sys.stdout.write(path)
    sys.stdout.write("\n")
    
