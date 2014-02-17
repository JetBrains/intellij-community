from datetime import datetime
from datetime import date

class MainParent(object):
    pass

class SubParent1(MainParent):
    def foo(self):
        pass
    pass

class SubParent2(MainParent):
    pass

class Child(SubParent1, SubParent2):
    def spam(self):
        pass
    pass

class NoParentsAllowed(datetime, object):
    def foo(self):
        pass
    pass


class NoMembers(object):
    pass

class BadMro(MainParent, object, SubParent1, SubParent2):
    pass

class HugeChild(SubParent1, date): #SubParent1 is disabled
    def __init__(self):
        self.instance_field_1 = 42
        self.instance_field_2 = 100500

    CLASS_FIELD = 42
    (CLASS_FIELD_A,CLASS_FIELD_B) = (42,100500) #We do not support tuples in class assignments for now (see ClassFieldsManager)
    def foo(self): #should be disabled
        pass
    def bar(self):
        pass

    @classmethod
    def static_1(cls): # Could be abstract in Py3K
        pass

    @staticmethod
    def static_2():  # Could be abstract in Py3K
        pass


    @staticmethod
        def bad_method(): #Code has errors, so method should be not be marked as static
            pass

class Bar(object):
    C = 1

class Foo(Bar):
    def __init__(self):
        self.foo = 12