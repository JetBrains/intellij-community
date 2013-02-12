# Django hackery to load the appropriate version of unittest

try:
    # check the system path first
    from unittest2 import *
except ImportError:
    if sys.version_info >= (2,7):
        # unittest2 features are native in Python 2.7
        from unittest import *
    else:
        # otherwise use our bundled version
        from datetime import tzinfo
