class SuppressingContext:
    def __enter__(self):
        ...
    def __exit__(self, exc_type, exc_val, exc_tb) -> bool:
        ...

def foo():
    with SuppressingContext() as st:
        foo()
        return 1
    return None