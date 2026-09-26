class A():
    def some_method(self) -> int:
        pass

class B(A):
    def some_method(self) -> int:
        <selection>return super().some_method()</selection>

