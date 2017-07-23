class MyType1(type):
    @staticmethod
    def __prepare__(metacls, name):
        return {}


class MyType2(type):
    @staticmethod
    def __prepare__(metacls, name, bases):
        return {}


class MyType3(type):
    @staticmethod
    def __prepare__(metacls, name, bases, **kwargs):
        return {}