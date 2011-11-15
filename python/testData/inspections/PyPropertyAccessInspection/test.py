class A(object):
  def s(self, v):
    self._v = v

  def g(self):
    return self._v

  def d(self):
    pass

  readonly = property(g)
  writeonly = property(None, s)
  readwrite = property(g, s, None, "Doc string")
  deleteble = property(g,s,d)

def eat(x): pass

a = A()

eat(a.readonly)
b = a.readonly

<warning descr="Property 'readonly' cannot be set">a.readonly</warning> += 1
del <warning descr="Property 'readonly' cannot be deleted">a.readonly</warning>
del <error descr="can't delete function call">a.readonly()</error> # Error, delete the result of function

a.writeonly = 1
<warning descr="Property 'writeonly' cannot be read">a.writeonly</warning> += 1
b = <warning descr="Property 'writeonly' cannot be read">a.writeonly</warning>
del <warning descr="Property 'writeonly' cannot be deleted">a.writeonly</warning>

b = a.readwrite
a.readwrite()
a.readwrite = 1
a.readwrite += 1
del <warning descr="Property 'readwrite' cannot be deleted">a.readwrite</warning>

del a.deletable

x += a.readonly #pass

#PY-2313
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
