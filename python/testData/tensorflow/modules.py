import tensorflow
root = tensorflow.compat.v1  # or tensorflow
module_type = type(tensorflow)
print('\n'.join('%s' % (name,)
                for name in dir(root)
                if not name.startswith('_')
                for module in (getattr(root, name),)
                if type(module) is module_type))