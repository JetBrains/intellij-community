
import sys
import traceback


def _import_module_patched(module_label, classnames):
    """
    Imports the module with the given name.
    Returns None if the module doesn't exist, but propagates any import errors.
    """
    try:
        return __import__(module_label, fromlist=classnames)
    except ImportError:
        # There are 2 reasons why there could be an ImportError:
        #
        #  1. Module does not exist. In that case, we ignore the import and
        #     return None
        #  2. Module exists but another ImportError occurred when trying to
        #     import the module. In that case, it is important to propagate the
        #     error.
        #
        # ImportError does not provide easy way to distinguish those two cases.
        # Fortunately, the traceback of the ImportError starts at __import__
        # statement. If the traceback has more than one frame, it means that
        # application was found and ImportError originates within the local app
        #
        # Changes in patch: change 1 to 2 frames because of the frame, added by
        # plugin_import.
        #
        __, __, exc_traceback = sys.exc_info()
        frames = traceback.extract_tb(exc_traceback)
        if len(frames) > 2:
            raise


def patch_oscar_loading():
    module = sys.modules['oscar.core.loading']
    setattr(module, '_import_module', _import_module_patched)
