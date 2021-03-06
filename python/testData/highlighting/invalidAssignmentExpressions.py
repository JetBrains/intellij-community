<error descr="Unparenthesized assignment expressions are prohibited at the top level of an expression statement">y := f(x)</error>  # INVALID
(y := f(x))  # Valid, though not recommended

y0 = <error descr="Unparenthesized assignment expressions are prohibited at the top level of the right hand side of an assignment statement">y1 := f(x)</error>  # INVALID
y0 = (y1 := f(x))  # Valid, though discouraged

class A:
    [<error descr="Assignment expressions within a comprehension cannot be used in a class body">y := i</error> for i in range(2)]