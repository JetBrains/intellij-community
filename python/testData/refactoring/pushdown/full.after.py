class Dummny(object):
    pass


class Parent(object):
    """
    This class is usefull.
    """
    CLASS_VAR_2 = 2

    def __init__(self):
        pass

    def method_2(self):
        i = 1


class Child_1(Parent, Dummny):
    CLASS_VAR_1 = 1

    def __init__(self):
        self.inst_var = 12
        self.bar = 64

    def method_1(self):
        """
        Some text
        """
        pass


class Child_2(Parent, Dummny):
    """
    This class implements most sophisticated algorithm
    """
    CLASS_VAR_1 = 1

    def __init__(self):
        self.inst_var = 12

    def lala(self):
        pass

    def method_1(self):
        """
        Some text
        """
        pass
