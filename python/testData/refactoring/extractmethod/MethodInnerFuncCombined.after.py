class Test:
    a = 5
    def method(self, b):
        def func(c):
            y = extracted(c)
            return y

        def extracted(c_new):
            y = self.a + b * c_new
            return y
