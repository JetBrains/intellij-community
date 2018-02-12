from collections import namedtuple


MyTup1 = namedtuple(<warning descr="Unexpected argument">bar=''</warning><warning descr="Parameter 'field_names' unfilled"><warning descr="Parameter 'typename' unfilled">)</warning></warning>
MyTup2 = namedtuple("MyTup2", "bar baz")


class MyTup3(namedtuple(<warning descr="Unexpected argument">bar=''</warning><warning descr="Parameter 'field_names' unfilled"><warning descr="Parameter 'typename' unfilled">)</warning></warning>):
   pass


class MyTup4(namedtuple("MyTup4", "bar baz")):
    pass


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
