from typing import Generator

def gen() -> Generator[str, bool, int]:
    b: bool = yield "str"
    return 42