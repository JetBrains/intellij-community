global_var = 'spam'


def enclosing(p1, p2):
    x = 42

    local(p1, x, 'foo')


def local(p1, x, p):
    def nested():
        print(p, x)

    print(p1, p)