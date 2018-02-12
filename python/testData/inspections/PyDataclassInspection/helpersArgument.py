import dataclasses
from typing import Type, Union


class A:
    pass


dataclasses.fields(<warning descr="'dataclasses.fields' method should be called on dataclass instances or types">A</warning>)
dataclasses.fields(<warning descr="'dataclasses.fields' method should be called on dataclass instances or types">A()</warning>)

dataclasses.asdict(<warning descr="'dataclasses.asdict' method should be called on dataclass instances">A()</warning>)
dataclasses.astuple(<warning descr="'dataclasses.astuple' method should be called on dataclass instances">A()</warning>)
dataclasses.replace(<warning descr="'dataclasses.replace' method should be called on dataclass instances">A()</warning>)


@dataclasses.dataclass
class B:
    pass


dataclasses.fields(B)
dataclasses.fields(B())

dataclasses.asdict(B())
dataclasses.astuple(B())
dataclasses.replace(B())

dataclasses.asdict(<warning descr="'dataclasses.asdict' method should be called on dataclass instances">B</warning>)
dataclasses.astuple(<warning descr="'dataclasses.astuple' method should be called on dataclass instances">B</warning>)
dataclasses.replace(<warning descr="'dataclasses.replace' method should be called on dataclass instances">B</warning>)


def unknown(p):
    dataclasses.fields(p)

    dataclasses.asdict(p)
    dataclasses.astuple(p)


def structural(p):
    print(len(p))
    dataclasses.fields(p)

    dataclasses.asdict(p)
    dataclasses.astuple(p)
    dataclasses.replace(p)


def union1(p: Union[A, B]):
    dataclasses.fields(p)

    dataclasses.asdict(p)
    dataclasses.astuple(p)
    dataclasses.replace(p)


def union2(p: Union[Type[A], Type[B]]):
    dataclasses.fields(p)

    dataclasses.asdict(<warning descr="'dataclasses.asdict' method should be called on dataclass instances">p</warning>)
    dataclasses.astuple(<warning descr="'dataclasses.astuple' method should be called on dataclass instances">p</warning>)
    dataclasses.replace(<warning descr="'dataclasses.replace' method should be called on dataclass instances">p</warning>)