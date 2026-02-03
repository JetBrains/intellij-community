from typing import Dict

class Parent:
    def overridable_method(self,
                           param  # type: str
                           ):  # type: (...) -> Dict[str, str]
        pass


class Child(Parent):
    def overridable_method(self,
                           param  # type: str
                           ):  # type: (...) -> Dict[str, str]