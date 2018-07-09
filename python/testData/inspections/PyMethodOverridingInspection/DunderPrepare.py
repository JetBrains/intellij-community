class MyType1(type):
    @classmethod
    def __prepare__(metacls, name):
        return {}


class MyType2(type):
    @classmethod
    def __prepare__(metacls, name, bases):
        return {}


class MyType3(type):
    @classmethod
    def __prepare__(metacls, name, bases, **kwargs):
        return {}