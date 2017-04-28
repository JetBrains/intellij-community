import typing


MyTup1 = typing.NamedTuple(<warning descr="Unexpected argument">bar=''</warning><warning descr="Parameter 'fields' unfilled"><warning descr="Parameter 'typename' unfilled">)</warning></warning>
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


# empty
MyTup2(<warning descr="Parameter 'bar' unfilled"><warning descr="Parameter 'baz' unfilled">)</warning></warning>

# one
MyTup2(bar=''<warning descr="Parameter 'baz' unfilled">)</warning>
MyTup2(baz=''<warning descr="Parameter 'bar' unfilled">)</warning>

# two
MyTup2('', '')
MyTup2(bar='', baz='')
MyTup2(baz='', bar='')

# three
MyTup2(bar='', baz='', <warning descr="Unexpected argument">foo=''</warning>)
MyTup2('', '', <warning descr="Unexpected argument">''</warning>)


# empty
MyTup3(<warning descr="Parameter 'bar' unfilled"><warning descr="Parameter 'baz' unfilled">)</warning></warning>

# one
MyTup3(bar=''<warning descr="Parameter 'baz' unfilled">)</warning>
MyTup3(baz=''<warning descr="Parameter 'bar' unfilled">)</warning>

# two
MyTup3('', '')
MyTup3(bar='', baz='')
MyTup3(baz='', bar='')

# three
MyTup3(bar='', baz='', <warning descr="Unexpected argument">foo=''</warning>)
MyTup3('', '', <warning descr="Unexpected argument">''</warning>)


# empty
MyTup4(<warning descr="Parameter 'bar' unfilled"><warning descr="Parameter 'baz' unfilled">)</warning></warning>

# one
MyTup4(bar=''<warning descr="Parameter 'baz' unfilled">)</warning>
MyTup4(baz=''<warning descr="Parameter 'bar' unfilled">)</warning>

# two
MyTup4('', '')
MyTup4(bar='', baz='')
MyTup4(baz='', bar='')

# three
MyTup4(bar='', baz='', <warning descr="Unexpected argument">foo=''</warning>)
MyTup4('', '', <warning descr="Unexpected argument">''</warning>)


# empty
MyTup5(<warning descr="Parameter 'bar' unfilled"><warning descr="Parameter 'baz' unfilled">)</warning></warning>

# one
MyTup5(bar=''<warning descr="Parameter 'baz' unfilled">)</warning>
MyTup5(baz=''<warning descr="Parameter 'bar' unfilled">)</warning>

# two
MyTup5('', '')
MyTup5(bar='', baz='')
MyTup5(baz='', bar='')

# three
MyTup5(bar='', baz='', <warning descr="Unexpected argument">foo=''</warning>)
MyTup5('', '', <warning descr="Unexpected argument">''</warning>)


# empty
MyTup6(<warning descr="Parameter 'bar' unfilled"><warning descr="Parameter 'baz' unfilled"><warning descr="Parameter 'foo' unfilled">)</warning></warning></warning>

# one
MyTup6(bar=''<warning descr="Parameter 'baz' unfilled"><warning descr="Parameter 'foo' unfilled">)</warning></warning>
MyTup6(baz=''<warning descr="Parameter 'bar' unfilled"><warning descr="Parameter 'foo' unfilled">)</warning></warning>

# two
MyTup6('', ''<warning descr="Parameter 'foo' unfilled">)</warning>
MyTup6(bar='', baz=''<warning descr="Parameter 'foo' unfilled">)</warning>
MyTup6(baz='', bar=''<warning descr="Parameter 'foo' unfilled">)</warning>

# three
MyTup6(bar='', baz='', foo='')
MyTup6('', '', '')
