class Test:
    def method(self):
        def func(x):
            y = extracted(x)
            return y

        def extracted(x_new):
            y = x_new * 2
            return y
