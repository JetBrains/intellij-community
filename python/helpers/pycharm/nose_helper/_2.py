from __future__ import print_function

import ConfigParser as configparser
import types
try:
    from cStringIO import StringIO
except ImportError:
    from StringIO import StringIO

class_types = (type, types.ClassType)

def reraise(exc_class, exc_val, tb):
    raise exc_class, exc_val, tb