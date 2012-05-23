class Foo:
        def __init__(self):
            self.tmp = False

        def extract_method(self, condition1, condition2, condition3, condition4):
            list = (1, 2, 3)
            a = 6
            b = False
            if a in list or self.tmp:
                if condition1:
                    print(condition1)
                if b is not condition2:
                    print(b)
            else:
                <selection>self.tmp2 = True
                if condition3:
                    print(condition3)
                if condition4:
                    print(condition4)
                print("misterious extract method test")</selection>
f = Foo()
f.extract_method(True, True, True, True)