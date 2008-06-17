# encoding: utf-8
"""
This thing tries to restore public interface of objects that don't have a python
source: C extensions and built-in objects. It does not reimplement the
'inspect' module, but complements it.

Since built-ins don't have many features that full-blown objects have, 
we do not support some fancier things like metaclasses.

We use certain kind of doc comments ("f(int) -> list") as a hint for functions'
input and output, especially in builtin functions.

This code has to work with CPython versions from 2.2 to 3.0, and hopefully with
compatible versions of Jython and IronPython.
"""

import sys
import os
import string
import stat
import types
import __builtin__

try:
  import inspect
except ImportError:
  inspect = None # it may fail

import re

string_mod = string

version = (
  (sys.hexversion & (0xff << 24)) >> 24,
  (sys.hexversion & (0xff << 16)) >> 16
)

if version[0] == 1:
  printable_chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~  \t\n\r\x0b\x0c'
else:
  printable_chars = string_mod.printable

DIGITS = string_mod.digits

if version[0] >= 3:
  string = "".__class__
  LETTERS = string_mod.ascii_letters
  
  def str_join(a_list, a_str):
    return string.join(a_str, a_list)
    
  def the_exec(source, context):
    exec(source, context)
    
else: # < 3.0
  LETTERS = string_mod.letters
  def str_join(a_list, a_str):
    return string_mod.join(a_list, a_str)

  def the_exec(source, context):
    exec (source) in context
    

#

DOC_FUNC_RE = re.compile("(?:.*\.)?(\w+)\(([^\)]*)\).*") # $1 = function name, $2 = arglist

SANE_REPR_RE = re.compile("[A-Za-z_][0-9A-Za-z_]*(?:\(.*\))?") # identifier with possible (...)

def _searchbases(cls, accum):
  # logic copied from inspect.py
  if cls not in accum:
    accum.append(cls)
    for x in cls.__bases__:
      _searchbases(x, accum)


def getmro(a_class):
  # logic copied from inspect.py
  "Returns a tuple of MRO classes."
  if hasattr(a_class, "__mro__"):
    return a_class.__mro__
  elif hasattr(a_class, "__bases__"):
    bases = []
    _searchbases(cls, bases)
    return tuple(bases)
  else:
    return tuple()


def getBases(a_class): # FIXME: test for classes that don't fit this scheme
  "Returns a sequence of class's bases."
  if hasattr(a_class, "__bases__"):
    return a_class.__bases__
  else:
    return ()


def isCallable(x):
  return hasattr(x, '__call__')

_prop_types = [type(property())]
try: _prop_types.append(types.GetSetDescriptorType)
except: pass

try: _prop_types.append(types.MemberDescriptorType)
except: pass

_prop_types = tuple(_prop_types)

def isProperty(x):
  return isinstance(x, _prop_types)
  
def isSaneRValue(x):
  return isinstance(x, (int, float, str, unicode, dict, tuple, list, types.NoneType))
  
SIMPLEST_TYPES = (int, float, str, unicode, types.NoneType)

class Redo(object):
  
  def __init__(self, outfile, indent_size=4):
    """
    Create new instance with given output file. The file must be writable.
    """
    self.outfile = outfile
    self.indent_size = indent_size
    self._indent_step = " " * indent_size
    
    
  def indent(self, level):
    "Return indentation whitespace for given level."
    return self._indent_step * level
    
    
  def out(self, what, indent=0):
    "Output the argument, indenting as nedded, and adding a eol"
    self.outfile.write(self.indent(indent));
    self.outfile.write(what);
    self.outfile.write("\n");
    
    
  def outDocstring(self, docstring, indent):
    if docstring is not None and isinstance(docstring, str):
      self.out('"""', indent)
      for line in docstring.split("\n"):
        self.out(line, indent)
      self.out('"""', indent)
    
    
  # Some values are known to be of no use in source and needs to be suppressed.
  # Dict is keyed by module names, with "*" meaning "any module";
  # values are lists of names of members whose value must be pruned.
  SKIP_VALUE_IN_MODULE = {
    "sys": ("modules", "path_importer_cache",),
    "*":   ("__builtins__",)
  }
  
  def isSkippedInModule(self, p_module, p_value):
    "Returns True if p_value's value must be skipped for module p_module."
    skip_list = self.SKIP_VALUE_IN_MODULE.get(p_module, [])
    if p_value in skip_list:
      return True
    skip_list = self.SKIP_VALUE_IN_MODULE.get("*", [])
    if p_value in skip_list:
      return True
    return False
    
    
  def fmtValue(self, p_value, indent, prefix="", postfix=""):
    if isinstance(p_value, SIMPLEST_TYPES):
      self.out(prefix + repr(p_value) + postfix, indent)
    elif isinstance(p_value, (list, tuple)):
      if len(p_value) == 0:
        self.out(prefix + repr(p_value) + postfix, indent)
      else:
        if isinstance(p_value, list):
          lpar, rpar = "[", "]"
        else:
          lpar, rpar = "(", ")"
        self.out(prefix + lpar, indent)
        for v in p_value:
          self.fmtValue(v, indent+1, postfix=",")
        self.out(rpar  + postfix, indent)
    elif isinstance(p_value, dict):
      if len(p_value) == 0:
        self.out(prefix + repr(p_value) + postfix, indent)
      else:
        self.out(prefix + "{", indent)
        for k in p_value:
          v = p_value[k]
          if isinstance(k, SIMPLEST_TYPES):
            self.fmtValue(v, indent+1, prefix=repr(k)+": ", postfix=",")
          else:
            # both key and value need fancy formatting
            self.fmtValue(k, indent+1, postfix=": ")
            self.fmtValue(v, indent+2)
            self.out(",", indent+1)
        self.out("}" + postfix, indent)
    else: # something else, maybe representable
      # TODO: handle imported objects somehow
      s = repr(p_value)
      if SANE_REPR_RE.match(s):
        self.out(prefix + s + postfix, indent)
      else:
        self.out(prefix + "None" + postfix + " # real value is " + s, indent)        
        
        

  def redoFunction(self, p_func, p_name, indent, p_class=None):
    """
    Restore function argument list as best we can.
    @param p_func function or method object
    @param p_name function name as known to owner
    @param indent indentation level
    @param p_class the class that contains this function as a method 
    """
    if inspect and inspect.isfunction(p_func):
      args, varg, kwarg, defaults = inspect.getargspec(p_func)
      spec = []
      dcnt = defaults and len(defaults)-1 or -1
      args = args or []
      args.reverse() # backwards, for easier defaults handling
      for arg in args: 
        if dcnt >= 0:
          arg += " = " + repr(defaults[dcnt])
          dcnt -= 1
        spec.insert(0, arg)
      if varg:
        spec.append("*" + varg)
      if kwarg:
        spec.append("**" + kwarg)
      self.out("def " + p_name + "(" + ", ".join(spec) + "): # reliably restored by inspect", indent);
      self.outDocstring(p_func.__doc__, indent+1)
      self.out("pass", indent+1);
    else:
      # __doc__ is our best source of arglist
      sig_note = "real signature unknown"
      spec = []
      if p_class is not None:
        spec.append("self")
      is_init = (p_name == "__init__" and p_class is not None)
      funcdoc = None
      if is_init and hasattr(p_class, "__doc__"):
        if hasattr(p_func, "__doc__"):
          funcdoc = p_func.__doc__
        if funcdoc == object.__init__.__doc__:
          funcdoc = p_class.__doc__
      elif hasattr(p_func, "__doc__"):
        funcdoc = p_func.__doc__
      if funcdoc:  
        m = DOC_FUNC_RE.search(funcdoc)
        if m:
          matches = m.groups()
          if matches[0] == p_name or is_init: 
            # they seem to really mention what we need
            sig_note = "restored from __doc__"
            if len(matches) > 1:
              arg_grp = matches[1].split("[") # from "a, b[, c]" we want ("a, b", "c")
              fixargs = arg_grp[0]
              for arg in fixargs.split(", "):
                if not arg:
                  continue # for ''
                arg = arg.strip("\"'") # doc might speak of f("foo")
                if arg == "...":
                  arg = "*more" # doc might speak of f(x, ...)
                spec.append(arg)
              # there could be "optional" args
              for optarg in arg_grp[1:]:
                cutpos = optarg.find("]")
                if cutpos != -1:
                  optarg = optarg[0 : cutpos] # cut possible final ]s
                if optarg.startswith(","): # probably it was "a[,b]" or "a [, b]" 
                  optarg = optarg[1:]
                optarg = optarg.strip()
                if optarg.find("*") == -1 and optarg.find("=") == -1: # simple argument, not "*x" or "x=1"
                  optarg += "=None" # NOTE: default value is not exactly good
                spec.append(optarg)
      else:
        funcdoc = None
      self.out("def " + p_name + "(" + ", ".join(spec) + "): # " + sig_note, indent);
      self.outDocstring(funcdoc, indent+1)
      self.out("pass", indent+1);
      
      
  def redoClass(self, p_class, p_name, indent):
    """
    Restores a class definition.
    @param p_class the class object
    @param p_name function name as known to owner
    @param indent indentation level
    """
    bases = getBases(p_class)
    base_def = ""
    if bases:
      base_def = "(" + ", ".join([x.__name__ for x in bases]) + ")"
    self.out("class " + p_name + base_def + ":", indent);
    self.outDocstring(p_class.__doc__, indent+1)
    # inner parts
    empty_body = True
    if hasattr(p_class, "__dict__"):
      # TODO: order item names more naturally. Sort first, output later
      for item_name in p_class.__dict__:
        if item_name in ("__dict__", "__doc__", "__module__"):
          continue
        item =  p_class.__dict__[item_name]
        if isCallable(item):
          self.redoFunction(item, item_name, indent+1, p_class)
          empty_body = False
        elif isProperty(item):
          self.out(item_name + " =  property(None, None, None)", indent+1); # TODO: handle docstring
          empty_body = False
        elif isSaneRValue(item):
          self.out(item_name + " = " + repr(item), indent+1);
          empty_body = False
        else: # can't handle
          self.out(item_name + " =  None # XXX can't represent: " + repr(item), indent+1);
          empty_body = False
        #  
        self.out("", 0) # empty line after each item
    if empty_body:
      self.out("pass", indent+1);
    
    
  def _checkInModules(self, item, modules):
    """
    Checks if item is represented in modules.
    @param item what to check
    @param modules a dict of modules
    @return a tuple (mod_name, name_in_it), or (None, None)
    """
    if not isinstance(item, SIMPLEST_TYPES):
      for mname in modules:
        m = modules[mname]
        for inner_name in m.__dict__:
          suspect = getattr(m, inner_name)
          if suspect is item:
            return (mname, inner_name)
    return (None, None)
    
  def redoModule(self, p_module, p_name):
    """
    Restores module declarations.
    Intended for built-in modules and thus does not handle import statements. 
    """
    self.out("# encoding: utf-8", 0); # NOTE: maybe encoding must be selectable
    self.out("# module " + p_name + " calls itself " + p_module.__name__, 0)
    if hasattr(p_module, "__file__"):
      self.out("# from file " + p_module.__file__, 0)
    self.outDocstring(p_module.__doc__, 0)
    # filter out whatever other modules the module knows
    modules = {"": __builtin__}
    for item_name in p_module.__dict__:
      item = p_module.__dict__[item_name]
      if isinstance(item, type(sys)):
        modules[item_name] = item
        if hasattr(item, "__name__"):
          self.out("import " + item.__name__ + " as " + item_name + " # refers to " + str(item))
        else:
          self.out(item_name + " = None # XXX name unknown, refers to " + str(item))
    self.out("", 0) # empty line after imports
    #
    for item_name in p_module.__dict__:
      if item_name in ("__dict__", "__doc__", "__module__"):
        continue
      item =  p_module.__dict__[item_name]
      # check if it has percolated from an imported module
      what_mod, what_name = self._checkInModules(item, modules)
      if what_mod is not None:
        if what_mod:
          what_mod += "."
        self.out(item_name + " = " + what_mod + what_name + " # reexported import", 0);
      else:
        # output it
        if isinstance(item, type): # some classes are callable, so they come first
          self.redoClass(item, item_name, 0)
        elif isCallable(item):
          self.redoFunction(item, item_name, 0)
        elif isinstance(item, type(sys)):
          continue # handled above already
        else:
          if self.isSkippedInModule(p_name, item_name):
            self.out(item_name + " = None # real value of type "+ str(type(item)) + " skipped", 0);
          else:
            self.fmtValue(item, 0, prefix = item_name + " = " );
        #  
        self.out("", 0) # empty line after each item
    
    
## simple test cases:
# import generator3 as g3
# import sys
# r = g3.Redo(sys.stdout)
# r.redoModule(sys, "sys")
# 
# from pysqlite2 import dbapi2
# r.redoModule(dbapi2, "dbapi2")
