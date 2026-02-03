def f(x):
    def <caret>g(y):
        print(x, y, z)
        g(x)
    z = 42
    g(42)   