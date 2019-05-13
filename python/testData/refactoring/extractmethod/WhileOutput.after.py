def f():
    a = 1
    while a < 10:
        a = bar(a)
    print(a)


def bar(a_new):
    do_smth
    a_new += 1
    return a_new
