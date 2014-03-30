class Spam:
    pass

class Parent_1(object, Spam):
    pass


class Parent_2():
    pass


class Child(Parent_1, Parent_2):
    pass