def f(x) -> int | None:
    y = 42
    print(y)
    if x == 1:
        return 42
    elif x == 2:
        return None
    elif x == 3:
        raise Exception()
    elif x == 4:
        assert False
    elif x == 5:
        assert x
        return None
    elif x == 4:
        return None
    return None