def decorator(f):
    return f

@decorator
def foo():
    return 'foo'

print(foo + 3) #pass
