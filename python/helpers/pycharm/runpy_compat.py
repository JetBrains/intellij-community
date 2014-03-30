import sys
from types import ModuleType
import os, imp
class ImpLoader:
  code = source = None

  def __init__(self, fullname, file, filename, etc):
    self.file = file
    self.filename = filename
    self.fullname = fullname
    self.etc = etc

  def load_module(self, fullname):
    self._reopen()
    try:
      mod = imp.load_module(fullname, self.file, self.filename, self.etc)
    finally:
      if self.file:
        self.file.close()
    return mod

  def get_data(self, pathname):
    return open(pathname, "rb").read()

  def _reopen(self):
    if self.file and self.file.closed:
      mod_type = self.etc[2]
      if mod_type==imp.PY_SOURCE:
        self.file = open(self.filename, 'rU')
      elif mod_type in (imp.PY_COMPILED, imp.C_EXTENSION):
        self.file = open(self.filename, 'rb')

  def _fix_name(self, fullname):
    if fullname is None:
      fullname = self.fullname
    elif fullname != self.fullname:
      raise ImportError("Loader for module %s cannot handle "
                          "module %s" % (self.fullname, fullname))
    return fullname

  def is_package(self, fullname):
    fullname = self._fix_name(fullname)
    return self.etc[2]==imp.PKG_DIRECTORY

  def get_code(self, fullname=None):
    fullname = self._fix_name(fullname)
    if self.code is None:
      mod_type = self.etc[2]
      if mod_type==imp.PY_SOURCE:
        source = self.get_source(fullname)
        self.code = compile(source, self.filename, 'exec')
      elif mod_type==imp.PY_COMPILED:
        self._reopen()
        try:
          self.code = read_code(self.file)
        finally:
          self.file.close()
      elif mod_type==imp.PKG_DIRECTORY:
        self.code = self._get_delegate().get_code()
    return self.code

  def get_source(self, fullname=None):
    fullname = self._fix_name(fullname)
    if self.source is None:
      mod_type = self.etc[2]
      if mod_type==imp.PY_SOURCE:
        self._reopen()
        try:
          self.source = self.file.read()
        finally:
          self.file.close()
      elif mod_type==imp.PY_COMPILED:
        if os.path.exists(self.filename[:-1]):
          f = open(self.filename[:-1], 'rU')
          self.source = f.read()
          f.close()
      elif mod_type==imp.PKG_DIRECTORY:
        self.source = self._get_delegate().get_source()
    return self.source


  def _get_delegate(self):
    return ImpImporter(self.filename).find_module('__init__')

  def get_filename(self, fullname=None):
    fullname = self._fix_name(fullname)
    mod_type = self.etc[2]
    if self.etc[2]==imp.PKG_DIRECTORY:
        return self._get_delegate().get_filename()
    elif self.etc[2] in (imp.PY_SOURCE, imp.PY_COMPILED, imp.C_EXTENSION):
        return self.filename
    return None


class ImpImporter:
  def __init__(self, path=None):
    self.path = path

  def find_module(self, fullname, path=None):
    # Note: we ignore 'path' argument since it is only used via meta_path
    subname = fullname.split(".")[-1]
    if subname != fullname and self.path is None:
      return None
    if self.path is None:
      path = None
    else:
      path = [os.path.realpath(self.path)]
    try:
      file, filename, etc = imp.find_module(subname, path)
    except ImportError:
      return None
    return ImpLoader(fullname, file, filename, etc)

  def iter_modules(self, prefix=''):
    if self.path is None or not os.path.isdir(self.path):
      return

    yielded = {}
    import inspect

    filenames = os.listdir(self.path)
    filenames.sort()  # handle packages before same-named modules

    for fn in filenames:
      modname = inspect.getmodulename(fn)
      if modname=='__init__' or modname in yielded:
        continue

      path = os.path.join(self.path, fn)
      ispkg = False

      if not modname and os.path.isdir(path) and '.' not in fn:
        modname = fn
        for fn in os.listdir(path):
          subname = inspect.getmodulename(fn)
          if subname=='__init__':
            ispkg = True
            break
        else:
          continue    # not a package

      if modname and '.' not in modname:
        yielded[modname] = 1
        yield prefix + modname, ispkg

def get_importer(path_item):
  try:
    importer = sys.path_importer_cache[path_item]
  except KeyError:
    for path_hook in sys.path_hooks:
      try:
        importer = path_hook(path_item)
        break
      except ImportError:
        pass
    else:
      importer = None
    sys.path_importer_cache.setdefault(path_item, importer)

  if importer is None:
    try:
      importer = ImpImporter(path_item)
    except ImportError:
      importer = None
  return importer

def iter_importers(fullname=""):
  if fullname.startswith('.'):
    raise ImportError("Relative module names not supported")
  if '.' in fullname:
    # Get the containing package's __path__
    pkg = '.'.join(fullname.split('.')[:-1])
    if pkg not in sys.modules:
      __import__(pkg)
    path = getattr(sys.modules[pkg], '__path__', None) or []
  else:
    for importer in sys.meta_path:
      yield importer
    path = sys.path
  for item in path:
    yield get_importer(item)
  if '.' not in fullname:
    yield ImpImporter()

def find_loader(fullname):
  for importer in iter_importers(fullname):
    loader = importer.find_module(fullname)
    if loader is not None:
      return loader

  return None

def get_loader(module_or_name):
  if module_or_name in sys.modules:
    module_or_name = sys.modules[module_or_name]
  if isinstance(module_or_name, ModuleType):
    module = module_or_name
    loader = getattr(module, '__loader__', None)
    if loader is not None:
      return loader
    fullname = module.__name__
  else:
    fullname = module_or_name
  return find_loader(fullname)


def _get_filename(loader, mod_name):
  for attr in ("get_filename", "_get_filename"):
    meth = getattr(loader, attr, None)
    if meth is not None:
      return meth(mod_name)
  return None

def _get_module_details(mod_name):
  loader = get_loader(mod_name)
  if loader is None:
    raise ImportError("No module named %s" % mod_name)
  if loader.is_package(mod_name):
    if mod_name == "__main__" or mod_name.endswith(".__main__"):
      raise ImportError("Cannot use package as __main__ module")
    try:
      pkg_main_name = mod_name + ".__main__"
      return _get_module_details(pkg_main_name)
    except ImportError, e:
      raise ImportError(("%s; %r is a package and cannot " +
                           "be directly executed") %(e, mod_name))
  code = loader.get_code(mod_name)
  if code is None:
    raise ImportError("No code object available for %s" % mod_name)
  filename = _get_filename(loader, mod_name)
  return mod_name, loader, code, filename

def _run_code(code, run_globals, init_globals=None,
              mod_name=None, mod_fname=None,
              mod_loader=None, pkg_name=None):
  if init_globals is not None:
      run_globals.update(init_globals)
  run_globals.update(__name__ = mod_name,
                     __file__ = mod_fname,
                     __loader__ = mod_loader,
                     __package__ = pkg_name)
  exec code in run_globals
  return run_globals

def run_module(mod_name, init_globals=None,
               run_name=None):
  mod_name, loader, code, fname = _get_module_details(mod_name)
  if run_name is None:
    run_name = mod_name

  ind = mod_name.rfind(".")
  if ind != -1:
    pkg_name = mod_name[:ind]
  else:
    pkg_name = mod_name
  return _run_code(code, {}, init_globals, run_name,
                       fname, loader, pkg_name)