from collections import namedtuple

def simple_func(cond):
    if cond:
        return 1
    else:
        return 1, 2

Point = namedtuple('Point', ['x', 'y'])
def named_tuple_func(cond):
    if cond:
        return 1
    else:
        return Point(1, 1)

def primitive_types_func(cond):
    if cond:
        return 1
    else:
        return 2

def collection_func(cond):
    if cond:
        return [1, 2]
    else:
        return {1, 2}

def list_tuple(cond):
    if cond:
        return [1, 2]
    else:
        return 1, 2

"%s %s" % simple_func(True)
"%s %s" % simple_func(False)
"%s %s %s" % <warning descr="Too few arguments for format string">simple_func(False)</warning>

"%s %s" % named_tuple_func(True)
"%s %s" % named_tuple_func(False)
"%s %s %s" % <warning descr="Too few arguments for format string">named_tuple_func(False)</warning>

"%s" % primitive_types_func(True)
"%s %s" % <warning descr="Too few arguments for format string">primitive_types_func(True)</warning>
"%s %s" % <warning descr="Too few arguments for format string">primitive_types_func(False)</warning>
"%s %s %s" % <warning descr="Too few arguments for format string">primitive_types_func(False)</warning>

"%s %s" % <warning descr="Too few arguments for format string">collection_func(True)</warning>
"%s %s" % <warning descr="Too few arguments for format string">collection_func(False)</warning>
"%s %s %s" % <warning descr="Too few arguments for format string">collection_func(False)</warning>

"%s %s" % list_tuple(True)
"%s %s" % list_tuple(True)
"%s %s %s" % <warning descr="Too few arguments for format string">list_tuple(True)</warning>

