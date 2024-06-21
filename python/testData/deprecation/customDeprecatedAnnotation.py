class deprecated():
    def __init__(
            self,
            message: str,
            /,
            *,
            category: type[Warning] | None = DeprecationWarning,
            stacklevel: int = 1,
    ) -> None:
        return None

    def __call__(self, arg, /):
        pass


@deprecated("deprecated")
def my_method():
    pass


my_method()