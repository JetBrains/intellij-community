class B:
    def __getattr__(self, name):
        pass

B().foo()
