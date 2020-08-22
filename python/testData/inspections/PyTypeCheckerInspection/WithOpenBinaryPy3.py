with open('foo', 'wb') as fd:
    fd.write(b'bar')

with open('foo', 'wb') as fd:
    fd.write(<weak_warning descr="Expected type 'bytes' (matched generic type 'AnyStr'), got 'str' instead">'bar'</weak_warning>)
