def test():
    print('foo' + 'bar')
    print(2 + 3)
    print('foo' + <warning descr="Expected type 'AnyStr', got 'int' instead">3</warning>)
    print(3 + <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>)
    print(3 + 3.14)
    print('foo' + 'bar' * 3)
    print('foo' + 3 * 'bar')
    print('foo' + <warning descr="Expected type 'AnyStr', got 'int' instead">2 * 3</warning>)
