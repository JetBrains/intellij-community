class A:
    def f(self, a, b):
        if a:
            b = 1
        elif b:
            pass
        else:
            a = 1
            print "a"