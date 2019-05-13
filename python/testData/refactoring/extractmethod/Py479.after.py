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
                self.bar(condition3, condition4)

        def bar(self, condition3_new, condition4_new):
            self.tmp2 = True
            if condition3_new:
                print(condition3_new)
            if condition4_new:
                print(condition4_new)
            print("misterious extract method test")


f = Foo()
f.extract_method(True, True, True, True)