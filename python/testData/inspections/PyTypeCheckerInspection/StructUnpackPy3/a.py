from struct import Struct

s = Struct('c')
s.unpack(<warning descr="Expected type 'bytes', got 'str' instead">'\x00'</warning>)
s.unpack(b'\x00')
