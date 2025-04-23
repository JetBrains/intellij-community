class NotSuppressingContext:
    def __enter__(self):
        ...
    def __exit__(self, exc_type, exc_val, exc_tb) -> bool | None:
        ...

def foo():
    with NotSuppressingContext() as st:
        foo()
        if bool():
            return 1
        return None