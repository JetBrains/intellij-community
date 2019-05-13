import typing


MyTup1 = typing.NamedTuple("MyTup1", bar=int, baz=int)
mt1 = MyTup1(1, 2)

# empty
mt1._replace()
MyTup1._replace(mt1)

# one
mt1._replace(bar=2)
mt1._replace(baz=1)
mt1._replace(<warning descr="Unexpected argument">foo=1</warning>)  
mt1._replace(<warning descr="Unexpected argument">1</warning>)
MyTup1._replace(mt1, bar=2)
MyTup1._replace(mt1, baz=1)
MyTup1._replace(mt1, <warning descr="Unexpected argument">foo=1</warning>)
MyTup1._replace(mt1, <warning descr="Unexpected argument">1</warning>)

# two
mt1._replace(bar=1, baz=2)
mt1._replace(baz=2, bar=1)
mt1._replace(baz=2, <warning descr="Unexpected argument">foo=1</warning>)  
mt1._replace(<warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">1</warning>)
MyTup1._replace(mt1, bar=1, baz=2)
MyTup1._replace(mt1, baz=2, bar=1)
MyTup1._replace(mt1, baz=2, <warning descr="Unexpected argument">foo=1</warning>)
MyTup1._replace(mt1, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">1</warning>)

# two
mt1._replace(bar=1, baz=2, <warning descr="Unexpected argument">foo=3</warning>)  
mt1._replace(<warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">3</warning>)
MyTup1._replace(mt1, bar=1, baz=2, <warning descr="Unexpected argument">foo=3</warning>)
MyTup1._replace(mt1, <warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">3</warning>)


class MyTup2(typing.NamedTuple):
    bar: int
    baz: int
mt2 = MyTup2(1, 2)

# empty
mt2._replace()
MyTup2._replace(mt2)

# one
mt2._replace(bar=2)
mt2._replace(baz=1)
mt2._replace(<warning descr="Unexpected argument">foo=1</warning>)  
mt2._replace(<warning descr="Unexpected argument">1</warning>)
MyTup2._replace(mt2, bar=2)
MyTup2._replace(mt2, baz=1)
MyTup2._replace(mt2, <warning descr="Unexpected argument">foo=1</warning>)
MyTup2._replace(mt2, <warning descr="Unexpected argument">1</warning>)

# two
mt2._replace(bar=1, baz=2)
mt2._replace(baz=2, bar=1)
mt2._replace(baz=2, <warning descr="Unexpected argument">foo=1</warning>)  
mt2._replace(<warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">1</warning>)
MyTup2._replace(mt2, bar=1, baz=2)
MyTup2._replace(mt2, baz=2, bar=1)
MyTup2._replace(mt2, baz=2, <warning descr="Unexpected argument">foo=1</warning>)
MyTup2._replace(mt2, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">1</warning>)

# two
mt2._replace(bar=1, baz=2, <warning descr="Unexpected argument">foo=3</warning>)  
mt2._replace(<warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">3</warning>)
MyTup2._replace(mt2, bar=1, baz=2, <warning descr="Unexpected argument">foo=3</warning>)
MyTup2._replace(mt2, <warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">3</warning>)