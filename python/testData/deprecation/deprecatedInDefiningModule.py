from typing_extensions import deprecated


@deprecated("lorem is deprecated")
def lorem() -> None:
    ...


<warning descr="lorem is deprecated">lorem</warning>()
