class SuppressingContext:
    def __enter__(self):
        ...
    def __exit__(self, exc_type, exc_val, exc_tb) -> bool:
        ...

def foo():
    <weak_warning descr="Explicit return statement expected">with SuppressingContext() as st:
        foo()
        return 1<caret></weak_warning>