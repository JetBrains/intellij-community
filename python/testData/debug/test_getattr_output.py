class A:

    def __getattr__(self, item):
        print(item)


a = A()
a.foo