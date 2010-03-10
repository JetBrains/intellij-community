a = 1

def foo():
    print <caret><warning descr="Local variable 'a' referenced before assignment">a</warning>
    a = 2
    print a

foo()
