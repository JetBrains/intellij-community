
# make settrace() function available for `pydevd_pycharm`
from pydevd import settrace

from _pydevd_bundle.pydevd_comm import VERSION_STRING
__version__ = VERSION_STRING
