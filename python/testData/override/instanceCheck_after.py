class MyType(type):
    def __instancecheck__(cls, instance):
        <selection>return super(MyType, cls).__instancecheck__(instance)</selection>
