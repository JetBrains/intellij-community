def test():
    xs = {'foo': 1, 'bar': 2}
    for v in xs.values():
        print(v + <warning descr="Expected type 'one of (int, long, float, complex)', got 'None' instead">None</warning>)
    for k in xs.keys():
        print(k + <warning descr="Expected type 'one of (str, unicode)', got 'None' instead">None</warning>)
    for k in xs:
        print(k + <warning descr="Expected type 'one of (str, unicode)', got 'None' instead">None</warning>)
    ys = filter(lambda x: x, xs.keys())
    ys.append(<weak_warning descr="Expected type 'str' (matched generic type 'T'), got 'int' instead">0</weak_warning>)
