def foo():
    l = [42 for <weak_warning descr="Local variable '_a' value is not used">_a</weak_warning> in xrange(100)]
    print(l)


def bar(_a):
    print(10)