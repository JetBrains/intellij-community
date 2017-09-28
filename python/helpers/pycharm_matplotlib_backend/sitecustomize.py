import matplotlib
matplotlib.use('module://backend_interagg')

# We want to import users sitecustomize.py file if any
import sys
import os

sitecustomize = "sitecustomize"
parent_dir = os.path.abspath(os.path.join(__file__, os.pardir))
if parent_dir in sys.path:
    sys.path.remove(parent_dir)

    if sitecustomize in sys.modules:
        pycharm_sitecustomize_module = sys.modules.pop(sitecustomize)

        try:
            import sitecustomize
        except ImportError:
            # return our module if we failed to find any other sitecustomize
            # to prevent KeyError importing 'site.py'
            sys.modules[sitecustomize] = pycharm_sitecustomize_module

    sys.path.append(parent_dir)
