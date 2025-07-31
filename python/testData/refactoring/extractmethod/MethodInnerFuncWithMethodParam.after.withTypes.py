class Test:
    def method(self, x):
        def func():
            y = extracted()
            return y

        def extracted() -> int | Any:
            y = x * 2
            return y
