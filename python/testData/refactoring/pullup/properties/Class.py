from SuperClass import SuperClass


class AnyClass(SuperClass):
    C = 1

    def __init__(self):
        super(AnyClass, self).__init__()


    @property
    def new_property(self):
        return 1

    @new_property.setter
    def new_property(self, value):
        pass

    @new_property.deleter
    def new_property(self):
        pass

    def foo(self):
        pass

