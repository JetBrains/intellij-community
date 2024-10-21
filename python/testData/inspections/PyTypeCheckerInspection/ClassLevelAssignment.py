from typing import ClassVar


class C:
    x: int
    y: int = 0
    z: int = 'foo'  # False negative!
    class_var: ClassVar[int]

    def f(self):
        self.x = 1
        self.x = <warning descr="Expected type 'int', got 'str' instead">'bar'</warning>
        self.y = 1
        self.y = <warning descr="Expected type 'int', got 'str' instead">'bar'</warning>
        self.z = 1
        self.z = <warning descr="Expected type 'int', got 'str' instead">'bar'</warning>

        self.class_var = 1
        self.class_var = <warning descr="Expected type 'int', got 'str' instead">'bar'</warning>
        C.class_var = 1
        C.class_var = <warning descr="Expected type 'int', got 'str' instead">'bar'</warning>
