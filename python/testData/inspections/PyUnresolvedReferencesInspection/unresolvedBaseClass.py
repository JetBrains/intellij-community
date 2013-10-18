class C(<error descr="Unresolved reference 'B'">B</error>):
    pass

c = C()
c.foo()