import typing
from typing import List


MyTup2 = typing.NamedTuple("MyTup2", bar=int, baz=str)
MyTup3 = typing.NamedTuple("MyTup2", [("bar", int), ("baz", str)])


class MyTup4(typing.NamedTuple):
    bar: int
    baz: str


class MyTup5(typing.NamedTuple):
    bar: int
    baz: str
    foo = 5


class MyTup6(typing.NamedTuple):
    bar: int
    baz: str
    foo: int


MyTup7 = typing.NamedTuple("MyTup7", names=List[str], ages=List[int])


# fail
MyTup2(<warning descr="Expected type 'int', got 'str' instead">''</warning>, '')
MyTup2(<warning descr="Expected type 'int', got 'str' instead">bar=''</warning>, baz='')
MyTup2(baz='', <warning descr="Expected type 'int', got 'str' instead">bar=''</warning>)


# ok
MyTup2(5, '')
MyTup2(bar=5, baz='')
MyTup2(baz='', bar=5)


# fail
MyTup3(<warning descr="Expected type 'int', got 'str' instead">''</warning>, '')
MyTup3(<warning descr="Expected type 'int', got 'str' instead">bar=''</warning>, baz='')
MyTup3(baz='', <warning descr="Expected type 'int', got 'str' instead">bar=''</warning>)


# ok
MyTup3(5, '')
MyTup3(bar=5, baz='')
MyTup3(baz='', bar=5)


# fail
MyTup4(<warning descr="Expected type 'int', got 'str' instead">''</warning>, '')
MyTup4(<warning descr="Expected type 'int', got 'str' instead">bar=''</warning>, baz='')
MyTup4(baz='', <warning descr="Expected type 'int', got 'str' instead">bar=''</warning>)


# ok
MyTup4(5, '')
MyTup4(bar=5, baz='')
MyTup4(baz='', bar=5)


# fail
MyTup5(<warning descr="Expected type 'int', got 'str' instead">''</warning>, '')
MyTup5(<warning descr="Expected type 'int', got 'str' instead">bar=''</warning>, baz='')
MyTup5(baz='', <warning descr="Expected type 'int', got 'str' instead">bar=''</warning>)


# ok
MyTup5(5, '')
MyTup5(bar=5, baz='')
MyTup5(baz='', bar=5)


# fail
MyTup6(<warning descr="Expected type 'int', got 'str' instead">bar=''</warning>, baz='', <warning descr="Expected type 'int', got 'str' instead">foo=''</warning>)
MyTup6(<warning descr="Expected type 'int', got 'str' instead">''</warning>, '', <warning descr="Expected type 'int', got 'str' instead">''</warning>)


# ok
MyTup6(bar=5, baz='', foo=5)
MyTup6(5, '', 5)


# fail
MyTup7(<warning descr="Expected type 'list[str]', got 'str' instead">names="A"</warning>, <warning descr="Expected type 'list[int]', got 'int' instead">ages=5</warning>)
MyTup7(<warning descr="Expected type 'list[str]', got 'str' instead">"A"</warning>, <warning descr="Expected type 'list[int]', got 'int' instead">5</warning>)


# ok
MyTup7(names=["A"], ages=[5])
MyTup7(["A"], [5])
