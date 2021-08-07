def foo():
    for x, y in [(1, 2)]:
        print(x)

def test_vlu():
    *<weak_warning descr="Local variable 'h' value is not used">h</weak_warning>, <weak_warning descr="Local variable 't' value is not used">t</weak_warning> = [1, 2, 3] # fail

def test_vlu():
    *h, t = [1, 2, 3] # pass
    print(t)