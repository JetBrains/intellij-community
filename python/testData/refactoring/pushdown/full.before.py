class Dummny(object):
    pass


class Parent(object, Dummny):
    """
    This class is usefull.
    """
    CLASS_VAR_1 = 1
    CLASS_VAR_2 = 2

    def __init__(self):
        self.inst_var = 12


    def method_1(self):
        """
        Some text
        """
        pass

    def method_2(self):
        i = 1


class Child_1(Parent):
    def __init__(self):
        self.bar = 64
    pass


class Child_2(Parent):
    """
    This class implements most sophisticated algorithm
    """
    def lala(self):
        pass
