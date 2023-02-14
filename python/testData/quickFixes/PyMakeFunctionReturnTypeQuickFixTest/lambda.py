def func() -> int:
    return <warning descr="Expected type 'int', got '(x: Any) -> int' instead">lambda x: 42<caret></warning>