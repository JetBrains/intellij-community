def foo() -> <warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8, 3.9 do not allow writing union types as X | Y">None | int<caret></warning>:
    return 42