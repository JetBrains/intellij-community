class C:
    def do(self, param: int) -> int:
        <caret>return self.body(param)

    def body(self, param_new):
        val = param_new * 2
        return val
