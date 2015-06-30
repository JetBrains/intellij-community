class A:
    def __init__(self):
        pass


class B(A):
    def __init__(self):
        A.__init__(self)
        # comment #1
        # comment #2
        print 42