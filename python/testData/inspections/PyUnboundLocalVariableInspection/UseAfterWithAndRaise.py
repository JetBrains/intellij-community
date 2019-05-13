def f(y, c, xs):
    if c:
        raise Exception()
    for x in xs:
        with y:
            pass
        print(x) #pass
