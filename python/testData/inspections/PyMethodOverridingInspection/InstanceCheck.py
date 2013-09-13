class MyType1(type):
    def __instancecheck__(cls, instance):
        return True


class MyType2(type):
    def __instancecheck__<warning descr="Signature of method 'MyType2.__instancecheck__()' does not match signature of base method in class 'type'">(cls)</warning>:
        return True


class MyType3(type):
    def __instancecheck__<warning descr="Signature of method 'MyType3.__instancecheck__()' does not match signature of base method in class 'type'">(cls, foo, bar)</warning>:
        return True
