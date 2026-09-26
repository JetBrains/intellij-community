class MyClass:
    def __init__(self, attr):
        self.attr = attr

    def __add__(self, other):
        return MyClass(self.attr + other.attr)

    def method_no_self(self):
        print('Can actually be static')


(MyClass(1) + MyClass(2)).met<caret>hod_no_self()