import typing


class MyTup5(typing.NamedTuple):
    bar: int
    baz: str = ""


# empty
MyTup5(<warning descr="Parameter 'bar' unfilled">)</warning>

# one
MyTup5('')
MyTup5(bar='')
MyTup5(baz=''<warning descr="Parameter 'bar' unfilled">)</warning>

# two
MyTup5('', '')
MyTup5(bar='', baz='')
MyTup5(baz='', bar='')

# three
MyTup5(bar='', baz='', <warning descr="Unexpected argument">foo=''</warning>)
MyTup5('', '', <warning descr="Unexpected argument">''</warning>)
