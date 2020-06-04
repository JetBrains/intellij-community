def foo():
    def <weak_warning descr="Local function 'bar' is not used">bar</weak_warning>(): pass #fail


def baz(cond):
    def bzz(): pass #pass
    if cond:
        return bzz
    else:
        return None

def bar():
    def <weak_warning descr="Local function 'bar1' is not used">bar1</weak_warning>():pass #fail
    def bar1():pass #pass
    return bar1

# PY-3866
def foo():
    def bar(): #pass
        pass
    def baz(): #pass
        bar()
    baz()


# PY-9778
def unused_inner_function_with_known_decorator():
    @staticmethod
    def <weak_warning descr="Local function 'func' is not used">func</weak_warning>():  # fail
        yield


def unused_inner_function_with_unknown_decorator():
    def register(f):
        return f

    @register
    @contextmanager
    def func(): # pass
        pass

def unused_inner_function_with_incomplete_decorator():
    @<EOLError descr="expression expected"></EOLError>
    def func(): # pass
        pass
