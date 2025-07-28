class Test:
    def method(self, a):
        def func(b):
            x = extracted(b)
            return x

        def extracted(b_new) -> Any:
            x = self.method(b_new)
            return x
