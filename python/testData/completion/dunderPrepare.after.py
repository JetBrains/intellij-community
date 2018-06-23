class A:
    @classmethod
    def __prepare__(metacls, name, bases):


class B:
    @classmethod
    def __prepare__(metacls, name, bases):


class C:
    @classmethod
    @decorator
    def __prepare__(metacls, name, bases):
