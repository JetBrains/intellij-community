def function1(a, b, argument=None):
    if argument is None:
        argument = a + b
    print(argument)

def function2():
    argument = None
    if argument is None:
        argument = 1 + 2
    print(argument)
