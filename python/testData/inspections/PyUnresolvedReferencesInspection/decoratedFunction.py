def decorator(f):
    return f

def f(x):
    return x

@decorator
def g(x):
    return x

print(f.<warning descr="Cannot find reference 'foo' in 'function'">foo</warning>) #fail
print(g.bar) #pass