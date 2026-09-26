def func() -> int:
    return <warning descr="Expected type 'int', got '(x: Unknown) -> Literal[42]' instead">lambda x: 42<caret></warning>