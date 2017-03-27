def test():
    class C():
        def f(self):
            return 2
    c = C()
    x = c.f()
    y = x
    return y + <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>
