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
    __metaclass__ = None # Anyway, this field should be ignored and processed separately as "metaclass", not "class field"

    def __init__(self):
        self.instance_field_1 = 42
        self.instance_field_2 = 100500

    CLASS_FIELD = 42
    (CLASS_FIELD_A,CLASS_FIELD_B) = (42,100500) #We do not support tuples in class assignments for now (see ClassFieldsManager)

    def _set(self, val): # Should not be treated as method (part of property)
        pass

    def _get(self):  # Should not be treated as method (part of property)
        return None

    name = property(fget=_get, fset=_set)


    @property
    def some_property(self): # Should not be treated as method (part of property)
        return None

    @some_property.setter
    def some_property(self, val): # Should not be treated as method (part of property)
        pass





    def foo(self): #should be disabled
        self.some_property = 12
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


class ParentWithConflicts(Bar):
    CLASS_FIELD = 42
    def __init__(self):
        self.instance_field = 12

    def my_func(self):
        pass


class ChildWithConflicts(ParentWithConflicts, Bar): # Bar -> conflict
    CLASS_FIELD = 42 # Conflict
    GOOD_FIELD = 32
    def __init__(self):
        self.instance_field = 12 # Conflict
        self.good_instance_field = "egg"

    def my_func(self): # Conflict
        pass