class NotSuppressingContext:
    def __enter__(self):
        ...
    def __exit__(self, exc_type, exc_val, exc_tb) -> bool | None:
        ...

def foo():
    with NotSuppressingContext() as st:
        foo()
        <weak_warning descr="Explicit return statement expected">if bool():
            return 1<caret></weak_warning>