def test_property_override_assignment():
    class B(object):
        @property
        def foo(self):
            return 0

        @property
        def bar(self):
            return -1

        @property
        def baz(self):
            return -2

    class C(B):
        foo = 'foo'

        def baz(self):
            return 'baz'

        def f(self, x):
            self.foo = x
            <warning descr="Property 'bar' cannot be set">self.bar</warning> = x
            self.baz = x
