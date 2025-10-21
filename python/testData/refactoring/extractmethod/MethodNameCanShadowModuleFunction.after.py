def _require_instance(type_):
    ...

class C:
    def do(self):
        self._require_instance()

    def _require_instance(self):
        print("x")
