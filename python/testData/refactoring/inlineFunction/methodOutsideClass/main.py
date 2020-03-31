class MyClass:

    def do_stuff(self, x, y):
        print(x)
        print(y)
        return self.for_inline(x, y)

    def for_inline(self, a, b):
        self.do_something_else()
        if a:
            print(a)
        elif b:
            print(b)
        else:
            print("nothing")
        return a, b

    def do_something_else(self):
        pass


x = 1
y = 2
cls = MyClass()
res = cls.for_in<caret>line(x, y)
