class Parent:
    def __init__(self):
        self.instance_field = "eggs"


class Child(Parent):
    def __init__(self):
        Parent2.__init__(self)

