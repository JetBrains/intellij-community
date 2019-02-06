class Parent:
    def overridable_method(param: str) -> Dict[str, str]:
        pass


class Child(Parent):
    def overridable_method(param: str) -> Dict[str, str]: