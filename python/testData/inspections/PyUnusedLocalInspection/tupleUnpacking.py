def foo():
    for x, y in [(1, 2)]:
        print x

def test_vlu():
    <error descr="Python versions < 3.0 do not support starred expressions as assignment targets">*<weak_warning descr="Local variable 'h' value is not used">h</weak_warning></error>, <weak_warning descr="Local variable 't' value is not used">t</weak_warning> = [1, 2, 3] # fail

def test_vlu():
    <error descr="Python versions < 3.0 do not support starred expressions as assignment targets">*h</error>, t = [1, 2, 3] # pass
    print(t)