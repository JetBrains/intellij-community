from typing import overload, Any, Union

class Clazz[T]:

    @overload
    def foo(self, a: str, b: str, c: str) -> Union[str, T]:
        pass

    @overload
    def foo(self, a: int, b: int, c: int) -> Union[int, T]:
        pass

    @overload
    def foo(self, a: str, b: int, c: bool) -> Union[float, T]:
        pass

    @overload
    def foo(self, a: str) -> None:
        pass

    def foo(self, a: Any, b: Any, c: Any) -> Union[str, int, bool, float, T]:
        pass