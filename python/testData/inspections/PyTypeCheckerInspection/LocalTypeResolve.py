def test():
    class C():
        def f(self):
            return 2
    c = C()
    x = c.f()
    y = x
    return y + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>
