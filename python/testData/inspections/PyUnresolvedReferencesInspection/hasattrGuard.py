class C(object):
    def __init__(self):
        self.foo = 1

    def f(self):
        self.bar = 2

    def g(self):
        if hasattr(self, 'baz'):
            return self.baz #pass
        else:
            return self.spam if hasattr(self, 'spam') else 'eggs' #pass

def main():
    c = C()
    c2 = C()
    try:
        if hasattr(c2, 'x'):
            d1 = c.<warning descr="Unresolved attribute reference 'x' for class 'C'">x</warning> #fail
            d2 = c2.x #pass
            return d1, d2
        if hasattr(c, 'spam'):
            def inner():
                c = C()
                return c.<warning descr="Unresolved attribute reference 'spam' for class 'C'">spam</warning> #fail
            return inner() + c.spam #pass
        if hasattr(c, 'f'):
            return c.f() #pass
        return c.<warning descr="Unresolved attribute reference 'spam' for class 'C'">spam</warning> #fail
    finally:
        if hasattr(c, 'close'):
            c.close() #pass

