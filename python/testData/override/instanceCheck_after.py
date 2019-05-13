class MyType(type):
    def __instancecheck__(self, instance):
        <selection>return super(MyType, self).__instancecheck__(instance)</selection>
