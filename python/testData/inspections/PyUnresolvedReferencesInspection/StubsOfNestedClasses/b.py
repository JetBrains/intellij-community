from c import Class1


class Class2(Class1):
    class SubClass2(Class1.SubClass1):
        def __init__(self, foo):
            Class1.SubClass1.__init__(self, foo)

