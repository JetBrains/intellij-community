try:
    import sys

    old_getfilesystemencoding = None
    if not sys.getfilesystemencoding():
        old_getfilesystemencoding = sys.getfilesystemencoding
        sys.getfilesystemencoding = lambda: 'UTF-8'

    import matplotlib

    if old_getfilesystemencoding:
        sys.getfilesystemencoding = old_getfilesystemencoding
    matplotlib.use('module://backend_interagg')

    # We want to import users sitecustomize.py file if any
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
except:
    # fallback in case matplotlib is not loaded correctly
    import sys

    for key in sys.modules.keys():
        if key.startswith("matplotlib"):
            sys.modules.pop(key)