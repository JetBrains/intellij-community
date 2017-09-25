def foo(x):
    t = [0, 1]
    try:
        print(t[2])
    except:
        pass
    return 1 / x


def zoo(x):
    res = foo(x)
    return res


print(zoo(0))
