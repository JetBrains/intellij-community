class A():
    def some_method(self) -> "return value":
        pass

class B(A):
    def some_method(self) -> "return value":
        <selection>super().some_method()</selection>

