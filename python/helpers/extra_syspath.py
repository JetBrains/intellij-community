import sys, os
qualified_name = sys.argv[-1]
path = qualified_name.split(".")

try:
  module =  __import__(qualified_name, globals(), locals(), [path[-1]])
  try:
    module_path = module.__path__
    if isinstance(module_path, str):
      sys.stdout.write(os.sep.join(module_path.split(os.sep)[:-1]))
    else:
      paths = (os.sep.join(p.split(os.sep)[:-1]) for p in module_path)
      sys.stdout.write(os.path.pathsep.join(paths))
    sys.stdout.flush()
  except AttributeError:
    pass
except ImportError:
  pass
