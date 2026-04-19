def function1(a, b, argument=None):
    if argument is None:
        argument = a + b
    print(argument)

def function2():
    argument = 42
    argument1 = None
    if argument1 is None:
        argument1 = 1 + 2
    print(argument1)
    print(argument)
