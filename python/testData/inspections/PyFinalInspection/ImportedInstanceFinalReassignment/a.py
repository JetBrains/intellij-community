from b import A, B

<warning descr="'a' is 'Final' and could not be reassigned">A().a</warning> = 3
<warning descr="'b' is 'Final' and could not be reassigned">B().b</warning> = 3

class C(B):
    def __init__(self):
        super().__init__()
        <warning descr="'B.b' is 'Final' and could not be reassigned">self.b</warning> = 4

    def my_method(self):
        <warning descr="'B.b' is 'Final' and could not be reassigned">self.b</warning> = 5

<warning descr="'B.b' is 'Final' and could not be reassigned">C().b</warning> = 6