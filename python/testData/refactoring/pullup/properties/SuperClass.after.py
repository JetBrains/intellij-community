class SuperClass(object):
    def __init__(self):
        pass

    @property
    def new_property(self):
        return 1

    @new_property.setter
    def new_property(self, value):
        pass

    @new_property.deleter
    def new_property(self):
        pass
