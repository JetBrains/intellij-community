from datetime import date
class Child(object, date):
    CLASS_VAR = "spam"

    def eggs(self):  # May be abstract
        pass

    def __init__(self):
        super(Child, self).__init__()
        self.artur = "king"

class StaticOnly(object):
    @staticmethod
    def static_method(): # May be abstract in case of Py3
        pass


class OldClass():
    def foo(self):
        pass