# PY-3639
def f(x):
    from <error descr="Unresolved reference 'foo'">foo</error> import <error descr="Unresolved reference 'StringIO'">StringIO</error>
    return StringIO(x)

def f(x):
    try:
        from <error descr="Unresolved reference 'foo'">foo</error> import <warning descr="Module 'StringIO' not found">StringIO</warning>
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
    import <warning descr="Module 'bar' not found">bar</warning>

# PY-3678
def f():
    try:
        from foo import bar #pass
    except ImportError:
        import <warning descr="Module 'bar' not found">bar</warning> #fail
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

# PY-8933: Import unreferenced outside the try block should not be reported
def f(x):
    try:
        from foo import StringIO
    except ImportError:
        pass
    return None

# PY-8933: Don't report cases where in case of the ImportError block being terminal
def f(x):
    try:
        from foo import StringIO
    except ImportError:
        raise
    return StringIO(x)


# PY-8933: Import unreferenced outside the try block should not be reported -- global scope
try:
    from foo import Unused
except ImportError:
    pass

# PY-8933: Import referenced by inner scope should be reported
try:
    from foo import <warning descr="'UsedInsideFunction' in try block with 'except ImportError' should also be defined in except block">UsedInsideFunction</warning>
except ImportError:
    pass

def f(x):
    return UsedInsideFunction(x)

# PY-8933: Do not report if imported name declared in parent scope
DeclaredAtFileScope = True
def f(x):
    try:
        from foo import DeclaredAtFileScope
    except ImportError:
        pass
    return DeclaredAtFileScope(x)

# PY-8203 do not report builtins
def f(x):
    try:
        from foo import any
    except ImportError:
        pass
    return any([1,2,3])
