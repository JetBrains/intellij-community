import sys, os
qualified_name = sys.argv[-1]
path = qualified_name.split(".")

try:
  module =  __import__(qualified_name, globals(), locals(), [path[-1]])
  try:
    p = module.__path__
    sys.stdout.write(os.sep.join(p.split(os.sep)[:-1]))
    sys.stdout.flush()
  except AttributeError:
    pass
except ImportError:
  pass
