class A:
    def f(self, a, b):
        while a:
            a = a / 2
        for i in range(1, 10):
            if a:
                b = 1
            elif b:
                a = 1
            else:
                print "a"
            print i