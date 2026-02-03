class A:
    def f(self, a, b):
        if a:
            b = 1
            a = 1
        elif b:
            pass
        else:
            print "a"