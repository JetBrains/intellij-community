def decorator(f):
    return f

@decorator
def foo():
    return 'foo'

print(<warning descr="Expected type 'int', got '() -> str' instead">foo</warning> + 3) # we know type at least
