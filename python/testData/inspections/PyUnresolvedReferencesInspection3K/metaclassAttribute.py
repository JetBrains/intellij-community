class Meta(type):
    def __init__(self, what, bases, dict):
        self.meta_attr = "attr"
        super().__init__(what, bases, dict)


class A(metaclass=Meta):
    pass


print(A.meta_attr)
print(A().meta_attr)