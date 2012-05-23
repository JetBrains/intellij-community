a = 2

def bar():
    global a
    a = a + 1
    c = a * 2
    return c

c = bar()
print(c)