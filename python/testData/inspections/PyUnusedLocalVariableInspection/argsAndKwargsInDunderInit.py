# PY-19491
class A(object):
    def __init__(self, <weak_warning descr="Parameter 'args' value is not used">*args</weak_warning>, <weak_warning descr="Parameter 'kwargs' value is not used">**kwargs</weak_warning>):
        self.foo = "foo"



# PY-19491
class B(object):
    def __init__(self, <weak_warning descr="Parameter 'args' value is not used">*args</weak_warning>, <weak_warning descr="Parameter 'kwargs' value is not used">**kwargs</weak_warning>):
        self.foo = "foo"


# PY-19491
class C(B):
    pass



# PY-19491
class D(object):
    def __init__(self, *args, **kwargs):
        self.foo = "foo"


# PY-19491
class E(D):
    def __init__(self, *args, **kwargs):
        super(E, self).__init__(*args, **kwargs)

