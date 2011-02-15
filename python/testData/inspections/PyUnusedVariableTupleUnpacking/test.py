def foo():
    for x, y in [(1, 2)]:
        print x

def test_vlu():
    <error descr="Python version 2.6 does not support this syntax. Starred expressions are not allowed as assignment targets in Python 2">*<warning descr="Local variable 'h' value is not used">h</warning></error>, <warning descr="Local variable 't' value is not used">t</warning> = [1, 2, 3] # fail

def test_vlu():
    <error descr="Python version 2.6 does not support this syntax. Starred expressions are not allowed as assignment targets in Python 2">*h</error>, t = [1, 2, 3] # pass
    print(t)