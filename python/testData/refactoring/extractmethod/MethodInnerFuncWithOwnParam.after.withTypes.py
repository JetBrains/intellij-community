class Test:
    def method(self):
        def func(x):
            y = extracted(x)
            return y

        def extracted(x_new) -> int | Any:
            y = x_new * 2
            return y
