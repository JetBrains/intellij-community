# PY-3639
def f(x):
    from <error descr="Unresolved reference 'foo'">foo</error> import <error descr="Unresolved reference 'StringIO'">StringIO</error>
    return StringIO(x)

def f(x):
    try:
        from <error descr="Unresolved reference 'foo'">foo</error> import <weak_warning descr="Module 'StringIO' not found">StringIO</weak_warning>
    except Exception:
        pass
    return x

def f(x):
    try:
        from foo import <warning descr="'StringIO' in try block with 'except ImportError' should also be defined in except block">StringIO</warning>
    except ImportError:
        pass
    return StringIO(x)

def f(x):
    try:
        from lib1 import StringIO
    except ImportError:
        StringIO = lambda x: x
    return StringIO(x)

# PY-3675
try:
    import foo as bar
except ImportError:
    import <weak_warning descr="Module 'bar' not found">bar</weak_warning>

# PY-3678
def f():
    try:
        from foo import bar #pass
    except ImportError:
        import <weak_warning descr="Module 'bar' not found">bar</weak_warning> #fail
    finally:
        pass

# PY-3869
def f(x):
    try:
        from foo import bar #pass
    except ImportError:
        def bar(x):
            return x
    return bar(x)

# PY-3919
def f(x):
    try:
        from foo import Bar #pass
    except ImportError:
        class Bar(object):
            pass
    return Bar()

