class MyType(type):
    def __instancecheck__(self, instance, /):
        <selection>return super().__instancecheck__(instance)</selection>
