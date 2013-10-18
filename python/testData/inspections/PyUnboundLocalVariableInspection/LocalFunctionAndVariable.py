def x(arg):
    def foo(): pass
    if arg: foo = None
    callee(foo) #pass
