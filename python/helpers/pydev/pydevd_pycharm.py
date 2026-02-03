
# make settrace() and stoptrace() functions available for `pydevd_pycharm`
from pydevd import settrace, stoptrace

from _pydevd_bundle.pydevd_comm import VERSION_STRING
__version__ = VERSION_STRING

__all__ = [
    'settrace',
    'stoptrace',
]
