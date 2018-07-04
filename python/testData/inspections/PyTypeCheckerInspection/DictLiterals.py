def test():
    xs = {'foo': 1, 'bar': 2}
    for v in xs.values():
        print(v + <warning descr="Expected type 'int', got 'None' instead">None</warning>)
    for k in xs.keys():
        print(k + <warning descr="Expected type 'AnyStr', got 'None' instead">None</warning>)
    for k in xs:
        print(k + <warning descr="Expected type 'AnyStr', got 'None' instead">None</warning>)
