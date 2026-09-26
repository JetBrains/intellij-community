from typing_extensions import deprecated

class Invocable:
    @deprecated("Use of deprecated method __call__")
    def __call__(self) -> None:
        pass

invocable = Invocable()
<warning descr="Use of deprecated method __call__">invocable</warning>()