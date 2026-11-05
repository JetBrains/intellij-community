def f():
    x = 1
    class A:
        nonlocal x
        x = 2
