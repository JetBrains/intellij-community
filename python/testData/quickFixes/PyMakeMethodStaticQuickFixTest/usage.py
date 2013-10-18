class A:
    def m<caret>(self, x):
        return x


print A.m(A(), 1)