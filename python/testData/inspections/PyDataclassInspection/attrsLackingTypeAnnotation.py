import attr

@attr.dataclass
class A1:
    <error descr="Attribute 'a' lacks a type annotation">a</error> = attr.ib()
    <error descr="Attribute 'b' lacks a type annotation">b</error> = attr.ib(type=int)
    c = 1
    d: int  = 1

@attr.s(auto_attribs=True)
class A3:
    <error descr="Attribute 'a' lacks a type annotation">a</error> = attr.ib()
    <error descr="Attribute 'b' lacks a type annotation">b</error> = attr.ib(type=int)
    c = 1
    d: int  = 1