def test():
    xs = {'foo': 1, 'bar': 2}
    for v in xs.values():
        print(v + <warning descr="Expected type 'int | long | float | complex', got 'None' instead">None</warning>)
    for k in xs.keys():
        print(k + <warning descr="Expected type 'str | unicode', got 'None' instead">None</warning>)
    for k in xs:
        print(k + <warning descr="Expected type 'str | unicode', got 'None' instead">None</warning>)
