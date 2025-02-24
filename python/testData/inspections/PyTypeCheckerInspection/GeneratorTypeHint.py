from typing import Generator

def fixture_generator() -> Generator[str, None, None]:
    yield "Hello World"