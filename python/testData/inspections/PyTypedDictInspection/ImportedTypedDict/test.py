from typing import TypedDict


class Base(TypedDict):
    a: int


class Test(Base, total=False):
    b: str


class Test1(Test, total=False):
    c: str
