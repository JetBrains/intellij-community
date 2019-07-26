def gen(xs):
    yield 42, <warning descr="Python version 2.6, 2.7, 3.4 do not support starred expressions in tuples, lists, and sets"><warning descr="Python version 3.5, 3.6, 3.7 do not support unpacking without parentheses in yield statements">*xs</warning></warning>
    <warning descr="Python version 2.6, 2.7 do not support this syntax. Delegating to a subgenerator is available since Python 3.3; use explicit iteration over subgenerator instead.">yield from 42, <error descr="Can't use starred expression here"><warning descr="Python version 2.6, 2.7, 3.4 do not support starred expressions in tuples, lists, and sets">*xs</warning></error></warning>


def func(xs):
    return 42, <warning descr="Python version 2.6, 2.7, 3.4 do not support starred expressions in tuples, lists, and sets"><warning descr="Python version 3.5, 3.6, 3.7 do not support unpacking without parentheses in return statements">*xs</warning></warning>
