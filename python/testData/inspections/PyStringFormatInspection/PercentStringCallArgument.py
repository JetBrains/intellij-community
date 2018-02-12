def bar():
    return 1
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

def bar():
    return 1.0
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

def bar():
    return ""
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

def bar():
    return True
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

def bar():
    return []
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

def bar():
    return {}
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

def bar():
    return set()
"%s %s" % <warning descr="Too few arguments for format string">bar()</warning>

