import os
import sys
import traceback
SHOW_DEBUG_INFO = os.getenv('PYCHARM_DEBUG', 'False').lower() in ['true', '1']


def debug(message):
    if SHOW_DEBUG_INFO:
        sys.stderr.write(message)
        sys.stderr.write("\n")


debug("Executing PyCharm's `sitecustomize`")
modules_list = []

try:
    # We want to import users sitecustomize.py file if any
    sitecustomize = "sitecustomize"
    parent_dir = os.path.abspath(os.path.join(__file__, os.pardir))
    if parent_dir in sys.path:
        sys.path.remove(parent_dir)

        if sitecustomize in sys.modules:
            pycharm_sitecustomize_module = sys.modules.pop(sitecustomize)

            try:
                import sitecustomize
            except ImportError:
                debug("User doesn't have a custom `sitecustomize`")
                # return our module if we failed to find any other sitecustomize
                # to prevent KeyError importing 'site.py'
                sys.modules[sitecustomize] = pycharm_sitecustomize_module

        sys.path.append(parent_dir)

    # Use matplotlib backend from pycharm
    modules_list = list(sys.modules.keys())
    old_getfilesystemencoding = None
    if not sys.getfilesystemencoding():
        old_getfilesystemencoding = sys.getfilesystemencoding
        sys.getfilesystemencoding = lambda: 'UTF-8'

    import matplotlib

    if old_getfilesystemencoding:
        sys.getfilesystemencoding = old_getfilesystemencoding
    matplotlib.use('module://backend_interagg')
    debug("Custom matplotlib backend was set for SciView")


except:
    # fallback in case matplotlib is not loaded correctly
    if SHOW_DEBUG_INFO:
        traceback.print_exc()

    keys = list(sys.modules.keys())
    if modules_list:
        for key in keys:
            if key not in modules_list:
                sys.modules.pop(key)
