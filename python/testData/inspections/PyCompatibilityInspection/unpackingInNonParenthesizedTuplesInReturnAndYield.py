def gen(xs):
    yield 42, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.5, 3.6, 3.7 do not support unpacking without parentheses in yield statements">*xs</warning></warning>
    <warning descr="Python version 2.7 does not support this syntax. Delegating to a subgenerator is available since Python 3.3; use explicit iteration over subgenerator instead.">yield from 42, <error descr="Can't use starred expression here"><warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets">*xs</warning></error></warning>


def func(xs):
    return 42, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.5, 3.6, 3.7 do not support unpacking without parentheses in return statements">*xs</warning></warning>
