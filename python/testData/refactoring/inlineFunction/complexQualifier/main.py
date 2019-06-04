class MyClass:
    def __init__(self, attr):
        self.attr = attr

    def __add__(self, other):
        return MyClass(self.attr + other.attr)

    def method(self):
        print(self.attr)
        print(self.attr)


(MyClass(1) + MyClass(2)).meth<caret>od()