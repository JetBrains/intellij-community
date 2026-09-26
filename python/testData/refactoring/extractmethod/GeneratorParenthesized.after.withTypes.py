from typing import Any, Generator


def bar() -> Generator[int, Any, None]:
    return (i + 1 for i in range(1,
                                 10))


_ = bar()