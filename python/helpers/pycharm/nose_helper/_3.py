import configparser
from io import StringIO

class_types = (type,)

def reraise(exc_class, exc_val, tb):
    raise exc_class(exc_val).with_traceback(tb)
