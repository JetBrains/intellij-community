def foo():
    for x, y in [(1, 2)]:
        print x

def test_vlu():
    <warning descr="Starred expressions are not allowed as assignment targets in Python 2">*<warning descr="Local variable 'h' value is not used">h</warning></warning>, <warning descr="Local variable 't' value is not used">t</warning> = [1, 2, 3] # fail

def test_vlu():
    <warning descr="Starred expressions are not allowed as assignment targets in Python 2">*h</warning>, t = [1, 2, 3] # pass
    print(t)