with open('foo', 'wb') as fd:
    fd.write(b'bar')

with open('foo', 'wb') as fd:
    fd.write(<warning descr="Expected type 'Union[bytes, bytearray]', got 'str' instead">'bar'</warning>)
