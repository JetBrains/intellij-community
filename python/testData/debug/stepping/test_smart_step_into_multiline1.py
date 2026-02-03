def f(x):
    return x


L = [1, 2, 3]

z = (
        f(0) + f(1) +
        L.pop() + f(2) + f(3) +
        f(4) + L.pop()
)
