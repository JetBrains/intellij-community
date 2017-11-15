with open('foo', 'wb') as fd:
    fd.write(b'bar')

with open('foo', 'wb') as fd:
    fd.write(<warning descr="Unexpected type(s):(str)Possible types:(bytes)(bytearray)">'bar'</warning>)
