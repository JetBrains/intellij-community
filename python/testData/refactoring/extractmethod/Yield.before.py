def f(xs):
    found = False
    <selection>for x in xs:
        yield x
        found = True</selection>
    print(found)