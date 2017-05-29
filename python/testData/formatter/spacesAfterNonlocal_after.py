def f(y):
    x = 42

    def g():
        nonlocal x, y
        x = 'spam'
        y += 1
