class MyClass:

    def do_stuff(self, x, y):
        print(x)
        print(y)
        self.do_something_else()
        if x:
            print(x)
        elif y:
            print(y)
        else:
            print("nothing")
        result = x, y
        return result

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
