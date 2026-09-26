def foo() -> <error descr="Python version 3.9 does not allow writing union types as X | Y">int | None | str</error>:
    return 42