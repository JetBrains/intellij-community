import traceback

try:
    from urllib import quote
except:
    from urllib.parse import quote

import pydevd_constants
import pydev_log

def to_number(x):
    if is_string(x):
        try:
            n = float(x)
            return n
        except ValueError:
            pass

        l = x.find('(')
        if l != -1:
            y = x[0:l-1]
            #print y
            try:
                n = float(y)
                return n
            except ValueError:
                pass
    return None

def compare_object_attrs(x, y):
    try:
        if x == y:
            return 0
        x_num = to_number(x)
        y_num = to_number(y)
        if x_num is not None and y_num is not None:
            if x_num - y_num<0:
                return -1
            else:
                return 1
        if '__len__' == x:
            return -1
        if '__len__' == y:
            return 1

        return x.__cmp__(y)
    except:
        if pydevd_constants.IS_PY3K:
            return (to_string(x) > to_string(y)) - (to_string(x) < to_string(y))
        else:
            return cmp(to_string(x), to_string(y))

def cmp_to_key(mycmp):
    'Convert a cmp= function into a key= function'
    class K(object):
        def __init__(self, obj, *args):
            self.obj = obj
        def __lt__(self, other):
            return mycmp(self.obj, other.obj) < 0
        def __gt__(self, other):
            return mycmp(self.obj, other.obj) > 0
        def __eq__(self, other):
            return mycmp(self.obj, other.obj) == 0
        def __le__(self, other):
            return mycmp(self.obj, other.obj) <= 0
        def __ge__(self, other):
            return mycmp(self.obj, other.obj) >= 0
        def __ne__(self, other):
            return mycmp(self.obj, other.obj) != 0
    return K

def is_string(x):
    if pydevd_constants.IS_PY3K:
        return isinstance(x, str)
    else:
        return isinstance(x, basestring)

def to_string(x):
    if is_string(x):
        return x
    else:
        return str(x)

def print_exc():
    if traceback:
        traceback.print_exc()

def quote_smart(s, safe='/'):
    if pydevd_constants.IS_PY3K:
        return quote(s, safe)
    else:
        if isinstance(s, unicode):
            s =  s.encode('utf-8')

        return quote(s, safe)
        
        


