__author__ = 'ktisha'
import os, sys
import imp

PYTHON_VERSION_MAJOR = sys.version_info[0]
PYTHON_VERSION_MINOR = sys.version_info[1]

ENABLE_DEBUG_LOGGING = False
if os.getenv("UTRUNNER_ENABLE_DEBUG_LOGGING"):
  ENABLE_DEBUG_LOGGING = True

def debug(what):
  if ENABLE_DEBUG_LOGGING:
    sys.stdout.writelines(str(what) + '\n')

def adjust_sys_path(add_script_parent=True, script_index=1):
  sys.path.pop(0)
  if add_script_parent:
    script_path = os.path.dirname(sys.argv[script_index])
    sys.path.insert(0, script_path)

def adjust_django_sys_path():
  sys.path.pop(0)
  script_path = sys.argv[-1]
  sys.path.insert(0, script_path)

def import_system_module(name):
  import __future__
  lib_path = os.path.dirname(sys.modules['__future__'].__file__)

  module_path = os.path.join(lib_path, name + '.py')
  if not os.path.exists(module_path):
    module_path = os.path.join(lib_path, name + '/')

  if os.path.exists(module_path):
    return imp.load_source('pycharm_' + name, module_path)

  return None

def getModuleName(prefix, cnt):
  return prefix + "%" + str(cnt)


