class Mark:
    name: str
    index: int
    line: int
    column: int
    buffer: str | None
    pointer: int
    def __init__(self, name: str, index: int, line: int, column: int, buffer: str | None, pointer: int) -> None: ...
    def get_snippet(self, indent: int = ..., max_length: int = ...) -> str | None: ...

class YAMLError(Exception): ...

class MarkedYAMLError(YAMLError):
    context: str | None
    context_mark: Mark | None
    problem: str | None
    problem_mark: Mark | None
    note: str | None
    def __init__(
        self,
        context: str | None = ...,
        context_mark: Mark | None = ...,
        problem: str | None = ...,
        problem_mark: Mark | None = ...,
        note: str | None = ...,
    ) -> None: ...
