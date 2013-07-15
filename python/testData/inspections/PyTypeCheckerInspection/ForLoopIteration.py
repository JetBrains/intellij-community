def test():
    for x in 'foo':
        pass

    for x in <warning descr="Expected 'collections.Iterable', got 'int' instead">42</warning>:
        pass
