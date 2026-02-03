class MyType1(type):
    @classmethod
    def __prepare__<warning descr="Signature of method 'MyType1.__prepare__()' does not match signature of the base method in class 'type'">(metacls, name)</warning>:
        return {}


class MyType2(type):
    @classmethod
    def __prepare__(metacls, name, bases):
        return {}


class MyType3(type):
    @classmethod
    def __prepare__(metacls, name, bases, **kwargs):
        return {}