import io
from typing import IO, Any, BinaryIO, ContextManager, Iterable, Mapping, Text, Tuple
from typing_extensions import Literal

from .core import BaseCommand

clickpkg: Any

class EchoingStdin:
    def __init__(self, input: BinaryIO, output: BinaryIO) -> None: ...
    def __getattr__(self, x: str) -> Any: ...
    def read(self, n: int = ...) -> bytes: ...
    def readline(self, n: int = ...) -> bytes: ...
    def readlines(self) -> list[bytes]: ...
    def __iter__(self) -> Iterable[bytes]: ...

def make_input_stream(input: bytes | Text | IO[Any] | None, charset: Text) -> BinaryIO: ...

class Result:
    runner: CliRunner
    exit_code: int
    exception: Any
    exc_info: Any | None
    stdout_bytes: bytes
    stderr_bytes: bytes
    def __init__(
        self,
        runner: CliRunner,
        stdout_bytes: bytes,
        stderr_bytes: bytes,
        exit_code: int,
        exception: Any,
        exc_info: Any | None = ...,
    ) -> None: ...
    @property
    def output(self) -> Text: ...
    @property
    def stdout(self) -> Text: ...
    @property
    def stderr(self) -> Text: ...

class CliRunner:
    charset: str
    env: Mapping[str, str]
    echo_stdin: bool
    mix_stderr: bool
    def __init__(
        self, charset: Text | None = ..., env: Mapping[str, str] | None = ..., echo_stdin: bool = ..., mix_stderr: bool = ...
    ) -> None: ...
    def get_default_prog_name(self, cli: BaseCommand) -> str: ...
    def make_env(self, overrides: Mapping[str, str] | None = ...) -> dict[str, str]: ...
    def isolation(
        self, input: bytes | Text | IO[Any] | None = ..., env: Mapping[str, str] | None = ..., color: bool = ...
    ) -> ContextManager[Tuple[io.BytesIO, io.BytesIO | Literal[False]]]: ...
    def invoke(
        self,
        cli: BaseCommand,
        args: str | Iterable[str] | None = ...,
        input: bytes | Text | IO[Any] | None = ...,
        env: Mapping[str, str] | None = ...,
        catch_exceptions: bool = ...,
        color: bool = ...,
        **extra: Any,
    ) -> Result: ...
    def isolated_filesystem(self) -> ContextManager[str]: ...
