def func():
    def local():
        pass


class C:
    def method(self):
        pass

    @staticmethod
    def static_method(x):
        pass

    @classmethod
    def class_method(self):
        pass

    @property
    def field(self):
        return self._x

    def __magic__(self):
        pass


class Base:
    def overridden_method(self):
        pass


class Subclass(Base):
    def overridden_method(self):
        super(Subclass, self).overridden_method()
        

class MyString(str):
    def upper(self):
        pass
