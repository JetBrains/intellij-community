from datetime import date
class Child(object, date):
    CLASS_VAR = "spam"

    def eggs(self):
        pass

    def __init__(self):
        super(Child, self).__init__()
        self.artur = "king"

class StaticOnly(object):
    @staticmethod
    def static_method():
        pass