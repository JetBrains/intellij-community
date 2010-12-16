from numbers import *

def to_number(x):
    if isinstance(x, Number):
        return x
    if (isinstance(x, basestring)):
        try:
            n = float(x)
            return n
        except ValueError:
            pass

        l = x.find('(')
        if (l != -1):
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
        x_num = to_number(x)
        y_num = to_number(y)
        if (x_num is not None and y_num is not None):
            return int(x_num - y_num)
        if ('__len__' == x):
            return -1
        if ('__len__' == y):
            return 1

        return x.__cmp__(y)
    except:
        return cmp(str(x), str(y))


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

