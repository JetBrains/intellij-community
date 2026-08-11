# Basic ticket case: assigning to an attribute that doesn't exist on the class.
class A:
    pass


a = A()
a.<warning descr="Unresolved attribute reference 'attr' for class 'A'">attr</warning> = 1


# Attribute defined in __init__ — fine.
class WithInit:
    def __init__(self):
        self.x = 1


wi = WithInit()
wi.x = 2


# Attribute defined in a non-__init__ method — still resolvable via findInstanceAttribute.
class WithReset:
    def reset(self):
        self.x = 0


wr = WithReset()
wr.x = 5


# Class attribute — fine.
class WithClassAttr:
    x = 0


wca = WithClassAttr()
wca.x = 5


# Attribute defined in a subclass — fine on subclass instance.
class Base:
    pass


class Derived(Base):
    def __init__(self):
        self.x = 1


d = Derived()
d.x = 2

# But not on the base instance.
b = Base()
b.<warning descr="Unresolved attribute reference 'x' for class 'Base'">x</warning> = 3


# self assignment inside __init__ — the assignment itself is the definition.
class SelfInit:
    def __init__(self):
        self.field = 1


# Classes with __setattr__ — anything goes.
class WithSetattr:
    def __setattr__(self, name, value):
        pass


ws = WithSetattr()
ws.anything = 5


# Classes with __getattr__ — also ignored (existing convention).
class WithGetattr:
    def __getattr__(self, name):
        return None


wg = WithGetattr()
wg.whatever = 1


# @DynamicAttrs docstring marker — ignored.
class DynamicAttrs:
    """
    @DynamicAttrs
    """
    pass


da = DynamicAttrs()
da.anything = 1


# Property — assignment to a property is fine (setter resolved).
class WithProperty:
    @property
    def value(self):
        return 0

    @value.setter
    def value(self, v):
        pass


wp = WithProperty()
wp.value = 1


# __slots__ — known slot is fine; unknown is reported by checkSlotsAndProperties,
# not by this check (avoids double-report).
class Slotted:
    __slots__ = ('a',)


s = Slotted()
s.a = 1


# Plain object() — not reported (preserved from PY-906).
o = object()
o.y = 5
