


def add1(x):
    return x + 1


def add10(x):
    return x + 10


for i in (add1(x) + add10(x) for x in range(3)):
    print(i)
