def f(mode):
    if mode == "i":
        return 1
    elif mode == "f":
        return 1.0
    elif mode == "s":
        return ""
    elif mode == "b":
        return True
    elif mode == "l":
        return []
    elif mode == "d":
        return {}
    elif mode == "set":
        return set()
"%s %s" % <warning descr="Too few arguments for format string">f("i")</warning>

def bar():
    return 1.0
"%s %s" % <warning descr="Too few arguments for format string">f("f")</warning>

def bar():
    return ""
"%s %s" % <warning descr="Too few arguments for format string">f("s")</warning>

def bar():
    return True
"%s %s" % <warning descr="Too few arguments for format string">f("b")</warning>

def bar():
    return []
"%s %s" % <warning descr="Too few arguments for format string">f("l")</warning>

def bar():
    return {}
"%s %s" % <warning descr="Too few arguments for format string">f("d")</warning>

def bar():
    return set()
"%s %s" % <warning descr="Too few arguments for format string">f("set")</warning>

