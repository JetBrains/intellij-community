def generator2():
    for i in range(4):
        yield i


def generator():
    a = 42 #breakpoint
    yield from generator2()
    return a

sum = 0
for i in generator():
    sum += i

print("The end")