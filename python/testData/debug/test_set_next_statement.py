def foo(a):
    a *= 2
    print(a)


x = 0
x += 1
x += 2
while x < 2:
    x += 1
    print(x)

foo(x)
print("x = %d" % x)
