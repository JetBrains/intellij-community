a = 2
b = 3


def bar():
    global a, c
    a = a + b
    c = a * 2


bar()
print(c)