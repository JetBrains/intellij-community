class A:
    def f(self, a, b):
        while a:
            a = a / 2
        if a:
            b = 1
        elif<caret> b:
            a = 1
        else:
            print "a"
        for i in range(1, 10):
            print i