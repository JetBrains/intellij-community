import os
import tensorflow


def module_to_path(module):
    file = module.__file__
    sep = os.sep
    return file[file.rfind("site-packages") + len("site-packages") + len(sep):-len("__init__.py") - len(sep)].replace(sep, ".")


root = tensorflow  # or tensorflow.compat.v1
module_type = type(tensorflow)
print('\n'.join('%s %s' % (name, module_to_path(module))
                for name in dir(root)
                if not name.startswith('_')
                for module in (getattr(root, name),)
                if isinstance(module, module_type)))