from time import sleep


def foo(x):
    print(x)

x = 0
print(x)
foo(x)
while x<2:
    x+=1
    print(x)

x+=10
print("x = %d" % x)
while True:
    sleep(0.1)