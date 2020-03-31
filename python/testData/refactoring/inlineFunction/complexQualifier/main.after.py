class MyClass:
    def __init__(self, attr):
        self.attr = attr

    def __add__(self, other):
        return MyClass(self.attr + other.attr)

    def method(self):
        print(self.attr)
        print(self.attr)


my_class = (MyClass(1) + MyClass(2))
print(my_class.attr)
print(my_class.attr)
