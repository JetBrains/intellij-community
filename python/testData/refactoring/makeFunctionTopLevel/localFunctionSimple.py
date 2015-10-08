global_var = 'spam'


def enclosing(p1, p2):
    x = 42

    def lo<caret>cal(p):
        def nested():
            print(p, x)

        print(p1, p)

    local('foo')