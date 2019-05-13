from b import Class2


class Class3(Class2):
    class SubClass3(Class2.SubClass2):
        def __init__(self, foo):
            Class2.SubClass2.__init__(self, foo)

        def test(self):
            print(self.foo)
