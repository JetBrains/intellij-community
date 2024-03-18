
# Supported in Python 3.11
def f(*args: <warning descr="Python versions 2.7, 3.7, 3.8, 3.9, 3.10 do not support starred expressions in type annotations">*Ts</warning>): pass
def f(*args: <warning descr="Python versions 2.7, 3.7, 3.8, 3.9, 3.10 do not support starred expressions in type annotations">*tuple[int, ...]</warning>): pass
def f(*args: <warning descr="Python versions 2.7, 3.7, 3.8, 3.9, 3.10 do not support starred expressions in type annotations">*tuple[int, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*Ts</warning></warning>]</warning>): pass

def f() -> tuple[<warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*Ts</warning></warning>]: pass
def f() -> tuple[int, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*Ts</warning></warning>]: pass
def f() -> tuple[int, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*tuple[int, ...]</warning></warning>]: pass

x<warning descr="Python version 2.7 does not support variable annotations">: tuple[<warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*Ts</warning></warning>]</warning>
x<warning descr="Python version 2.7 does not support variable annotations">: tuple[int, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*Ts</warning></warning>]</warning>
x<warning descr="Python version 2.7 does not support variable annotations">: tuple[int, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*tuple[str, ...]</warning></warning>]</warning>

# Error in Python 3.11
def f(x: <error descr="Can't use starred expression here">*b</error>): pass
def f8(**kwargs: <error descr="Can't use starred expression here">*a</error>): pass
x<warning descr="Python version 2.7 does not support variable annotations">: <error descr="Can't use starred expression here">*b</error></warning>