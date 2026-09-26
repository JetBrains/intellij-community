def function1(a, b, argument=None):
    if argument is None:
        argument = a + b
    print(argument)

def function2():
    argument = 42
    function1<caret>(1, 2)
    print(argument)
