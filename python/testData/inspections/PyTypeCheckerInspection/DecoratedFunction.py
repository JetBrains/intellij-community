def decorator(f):
    return f

@decorator
def foo():
    return 'foo'

print(<warning descr="Expected type 'int', got '() -> Union[str, Any]' instead">foo</warning> + 3) # we know type at least
