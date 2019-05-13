class C:
    def f(self):
        pass

    __call__ = f


c = C()
c() # pass
