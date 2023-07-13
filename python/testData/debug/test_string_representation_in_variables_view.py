class FooStr:
    def __str__(self):
        return "str"


class FooRepr:
    def __repr__(self):
        return "repr"


class FooReprlib:
    pass


foo_str = FooStr()
foo_repr = FooRepr()
foo_reprlib = FooReprlib()
print()
