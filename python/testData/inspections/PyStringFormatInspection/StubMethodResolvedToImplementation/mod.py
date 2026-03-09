class A:
    def __str__(self):
        return "foo"
    def __format__(self, format_spec):
        return "foo"

class B(A): ...