from SuperClass import SuperClass


class AnyClass(SuperClass):
    C = 1

    def __init__(self):
        super(AnyClass, self).__init__()

    def foo(self):
        pass

