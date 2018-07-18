import attr
from typing import Type, Union


class A:
    pass


attr.fields(<warning descr="'attr.fields' method should be called on attrs types">A</warning>)
attr.fields(<warning descr="'attr.fields' method should be called on attrs types">A()</warning>)

attr.fields_dict(<warning descr="'attr.fields_dict' method should be called on attrs types">A</warning>)
attr.fields_dict(<warning descr="'attr.fields_dict' method should be called on attrs types">A()</warning>)

attr.asdict(<warning descr="'attr.asdict' method should be called on attrs instances">A()</warning>)
attr.astuple(<warning descr="'attr.astuple' method should be called on attrs instances">A()</warning>)
attr.assoc(<warning descr="'attr.assoc' method should be called on attrs instances">A()</warning>)
attr.evolve(<warning descr="'attr.evolve' method should be called on attrs instances">A()</warning>)


@attr.s
class B:
    pass


attr.fields(B)
attr.fields(<warning descr="'attr.fields' method should be called on attrs types">B()</warning>)

attr.fields_dict(B)
attr.fields_dict(<warning descr="'attr.fields_dict' method should be called on attrs types">B()</warning>)

attr.asdict(B())
attr.astuple(B())
attr.assoc(B())
attr.evolve(B())

attr.asdict(<warning descr="'attr.asdict' method should be called on attrs instances">B</warning>)
attr.astuple(<warning descr="'attr.astuple' method should be called on attrs instances">B</warning>)
attr.assoc(<warning descr="'attr.assoc' method should be called on attrs instances">B</warning>)
attr.evolve(<warning descr="'attr.evolve' method should be called on attrs instances">B</warning>)


def unknown(p):
    attr.fields(p)
    attr.fields_dict(p)

    attr.asdict(p)
    attr.astuple(p)


def structural(p):
    print(len(p))
    attr.fields(p)
    attr.fields_dict(p)

    attr.asdict(p)
    attr.astuple(p)
    attr.assoc(p)
    attr.evolve(p)


def union1(p: Union[A, B]):
    attr.fields(<warning descr="'attr.fields' method should be called on attrs types">p</warning>)
    attr.fields_dict(<warning descr="'attr.fields_dict' method should be called on attrs types">p</warning>)

    attr.asdict(p)
    attr.astuple(p)
    attr.assoc(p)
    attr.evolve(p)


def union2(p: Union[Type[A], Type[B]]):
    attr.fields(p)
    attr.fields_dict(p)

    attr.asdict(<warning descr="'attr.asdict' method should be called on attrs instances">p</warning>)
    attr.astuple(<warning descr="'attr.astuple' method should be called on attrs instances">p</warning>)
    attr.assoc(<warning descr="'attr.assoc' method should be called on attrs instances">p</warning>)
    attr.evolve(<warning descr="'attr.evolve' method should be called on attrs instances">p</warning>)