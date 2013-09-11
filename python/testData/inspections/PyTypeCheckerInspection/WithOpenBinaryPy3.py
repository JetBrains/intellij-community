with open('foo', 'wb') as fd:
    fd.write(b'bar')

with open('foo', 'wb') as fd:
    fd.write(<warning descr="Expected type 'bytes', got 'str' instead">'bar'</warning>)
