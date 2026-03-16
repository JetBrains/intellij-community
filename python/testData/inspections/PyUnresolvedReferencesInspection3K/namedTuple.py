from collections import namedtuple

Point = namedtuple('Point', ['x', 'y'], verbose=True)

print(Point.x, Point.y)

p = Point(11, y=22)
print(p.x + p.y + p.<warning descr="Unresolved attribute reference 'z' for class 'Point'">z</warning>)
print(p.__add__)
print(p._asdict())
print(Point._fields)
print(p._replace)

if isinstance(p, Point):
    p.x

class C(namedtuple('C', 'x y')):
    def f(self):
        return self

c = C()
print(c.x, c.y, c.<warning descr="Unresolved attribute reference 'z' for class 'C'">z</warning>, c.f())


# At runtime, the namedtuple function disallows field names that begin with an underscore or are illegal Python
# identifiers, and either raises an exception or replaces these fields with a parameter name of the form _N.

BadFields = namedtuple("BadFields", ["a", "raise", "_b", "c"], rename=True)
bad_fields = BadFields(0, 1, 2, 3)
print(bad_fields.a, bad_fields._1, bad_fields._2, bad_fields.c)
print(bad_fields.<warning descr="Unresolved attribute reference '_b' for class 'BadFields'">_b</warning>)


from typing import NamedTuple

class NT(NamedTuple):
    x: int
    y: int

    def f(self):
        return self

nt = NT()
print(nt.x, nt.y, nt.<warning descr="Unresolved attribute reference 'z' for class 'NT'">z</warning>, nt.f())
