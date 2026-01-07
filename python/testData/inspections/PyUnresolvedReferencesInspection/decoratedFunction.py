def decorator(f):
    return f

def f(x):
    return x

@decorator
def g(x):
    return x

print(f.<warning descr="Cannot find reference 'foo' in '(x: Any) -> Any'">foo</warning>) #fail
print(g.bar) #pass