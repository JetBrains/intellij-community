class A:
    def f(self, a, b):
        if a:
            b = 1
        elif b:
            a = <caret>1
        else:
            print "a"