# PY-20280: one slot in list
class Foo(object):
    __slots__ = [<warning descr="'foo' in __slots__ conflicts with class variable">'foo'</warning>]
    foo = 1


# PY-20280: one slot in tuple
class Foo(object):
    __slots__ = (<warning descr="'foo' in __slots__ conflicts with class variable">'foo'</warning>)
    foo = 1


# PY-20280: one slot
class Foo(object):
    __slots__ = <warning descr="'foo' in __slots__ conflicts with class variable">'foo'</warning>
    foo = 1


# PY-20280: two slots in list
class Foo(object):
    __slots__ = [<warning descr="'foo' in __slots__ conflicts with class variable">'foo'</warning>, 'bar']
    foo = 1


# PY-20280: two slots in tuple
class Foo(object):
    __slots__ = (<warning descr="'foo' in __slots__ conflicts with class variable">'foo'</warning>, 'bar')
    foo = 1


# PY-20280: slots in base and class variable in derived
class Base(object):
    __slots__ = 'foo'

class Derived(Base):
    foo = 1


# PY-20280: class variable in base and slots in derived
class Base(object):
    foo = 1

class Derived(Base):
    __slots__ = 'foo'


# PY-20280: slots in base and derived, class variable in derived
class Base(object):
    __slots__ = 'foo'

class Derived(Base):
    __slots__ = 'bar'
    foo = 1


# PY-20280: slots in base and derived, class variable in base
class Base(object):
    __slots__ = 'bar'
    foo = 1

class Derived(Base):
    __slots__ = 'foo'