class Test:
    x = 5

    def method(self):
        def func():
            y = extracted()
            return y

        def extracted() -> int:
            y = self.x
            return y
