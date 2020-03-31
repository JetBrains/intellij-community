<error descr="Unparenthesized assignment expressions are prohibited at the top level of an expression statement">y := f(x)</error>  # INVALID
(y := f(x))  # Valid, though not recommended

y0 = <error descr="Unparenthesized assignment expressions are prohibited at the top level of the right hand side of an assignment statement">y1 := f(x)</error>  # INVALID
y0 = (y1 := f(x))  # Valid, though discouraged

foo(x = <error descr="Unparenthesized assignment expressions are prohibited for the value of a keyword argument in a call">y := f(x)</error>)  # INVALID
foo(x=(y := f(x)))  # Valid, though probably confusing

def foo(answer = <error descr="Unparenthesized assignment expressions are prohibited at the top level of a function default value">p := 42</error>):  # INVALID
    pass
def foo(answer=(p := 42)):  # Valid, though not great style
    pass

def foo(answer: <error descr="Unparenthesized assignment expressions are prohibited as annotations for arguments, return values and assignments">p := 42</error> = 5):  # INVALID
    pass
def foo(answer: (p := 42) = 5):  # Valid, but probably never useful
    pass

(lambda: <error descr="Unparenthesized assignment expressions are prohibited at the top level of a lambda function">x := 1</error>) # INVALID
lambda: (x := 1) # Valid, but unlikely to be useful
(x := lambda: 1) # Valid
lambda line: (m := re.match(pattern, line)) and m.group(1) # Valid

class A:
    [<error descr="Assignment expressions within a comprehension cannot be used in a class body">y := i</error> for i in range(2)]