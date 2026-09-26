class A:
    x = 1


A.x = 2
A.y = 1


class Parent:
    x = 1


class Child(Parent):
    pass


Child.x = 2
Child.z = 1
