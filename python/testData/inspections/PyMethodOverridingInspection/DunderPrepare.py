class MyType1(type):
    @staticmethod
    def __prepare__<warning descr="Signature of method 'MyType1.__prepare__()' does not match signature of base method in class 'type'">(metacls, name)</warning>:
        return {}


class MyType2(type):
    @staticmethod
    def __prepare__(metacls, name, bases):
        return {}


class MyType3(type):
    @staticmethod
    def __prepare__(metacls, name, bases, **kwargs):
        return {}