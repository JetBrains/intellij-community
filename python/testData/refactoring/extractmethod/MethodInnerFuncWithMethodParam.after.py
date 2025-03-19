class Test:
    def method(self, x):
        def func():
            y = extracted()
            return y

        def extracted():
            y = x * 2
            return y
