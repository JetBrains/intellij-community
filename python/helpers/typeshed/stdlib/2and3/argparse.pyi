# Stubs for argparse (Python 3.4)

from typing import (
    Any, Callable, Iterable, List, IO, Optional, Sequence, Tuple, Type, Union,
    TypeVar, overload
)
import sys

_T = TypeVar('_T')


ONE_OR_MORE = ...  # type: str
OPTIONAL = ...  # type: str
PARSER = ...  # type: str
REMAINDER = ...  # type: str
SUPPRESS = ...  # type: str
ZERO_OR_MORE = ...  # type: str

class ArgumentError(Exception): ...

class ArgumentParser:
    if sys.version_info >= (3, 5):
        def __init__(self,
                     prog: Optional[str] = ...,
                     usage: Optional[str] = ...,
                     description: Optional[str] = ...,
                     epilog: Optional[str] = ...,
                     parents: Sequence[ArgumentParser] = ...,
                     formatter_class: Type[HelpFormatter] = ...,
                     prefix_chars: str = ...,
                     fromfile_prefix_chars: Optional[str] = ...,
                     argument_default: Optional[str] = ...,
                     conflict_handler: str = ...,
                     add_help: bool = ...,
                     allow_abbrev: bool = ...) -> None: ...
    else:
        def __init__(self,
                     prog: Optional[str] = ...,
                     usage: Optional[str] = ...,
                     description: Optional[str] = ...,
                     epilog: Optional[str] = ...,
                     parents: Sequence[ArgumentParser] = ...,
                     formatter_class: Type[HelpFormatter] = ...,
                     prefix_chars: str = ...,
                     fromfile_prefix_chars: Optional[str] = ...,
                     argument_default: Optional[str] = ...,
                     conflict_handler: str = ...,
                     add_help: bool = ...) -> None: ...
    def add_argument(self,
                     *name_or_flags: Union[str, Sequence[str]],
                     action: Union[str, Type[Action]] = ...,
                     nargs: Union[int, str] = ...,
                     const: Any = ...,
                     default: Any = ...,
                     type: Union[Callable[[str], _T], FileType] = ...,
                     choices: Iterable[_T] = ...,
                     required: bool = ...,
                     help: str = ...,
                     metavar: Union[str, Tuple[str, ...]] = ...,
                     dest: str = ...,
                     version: str = ...) -> None: ...  # weirdly documented
    def parse_args(self, args: Optional[Sequence[str]] = ...,
                   namespace: Optional[Namespace] = ...) -> Namespace: ...
    def add_subparsers(self, title: str = ...,
                       description: Optional[str] = ...,
                       prog: str = ...,
                       parser_class: Type[ArgumentParser] = ...,
                       action: Type[Action] = ...,
                       option_string: str = ...,
                       dest: Optional[str] = ...,
                       help: Optional[str] = ...,
                       metavar: Optional[str] = ...) -> _SubParsersAction: ...
    def add_argument_group(self, title: Optional[str] = ...,
                           description: Optional[str] = ...) -> _ArgumentGroup: ...
    def add_mutually_exclusive_group(self, required: bool = ...) -> _MutuallyExclusiveGroup: ...
    def set_defaults(self, **kwargs: Any) -> None: ...
    def get_default(self, dest: str) -> Any: ...
    def print_usage(self, file: Optional[IO[str]] = ...) -> None: ...
    def print_help(self, file: Optional[IO[str]] = ...) -> None: ...
    def format_usage(self) -> str: ...
    def format_help(self) -> str: ...
    def parse_known_args(self, args: Optional[Sequence[str]] = ...,
                         namespace: Optional[Namespace] = ...) -> Tuple[Namespace, List[str]]: ...
    def convert_arg_line_to_args(self, arg_line: str) -> List[str]: ...
    def exit(self, status: int = ..., message: Optional[str] = ...) -> None: ...
    def error(self, message: str) -> None: ...

class HelpFormatter:
    # not documented
    def __init__(self, prog: str, indent_increment: int = ...,
                 max_help_position: int = ...,
                 width: Optional[int] = ...) -> None: ...
class RawDescriptionHelpFormatter(HelpFormatter): ...
class RawTextHelpFormatter(HelpFormatter): ...
class ArgumentDefaultsHelpFormatter(HelpFormatter): ...
if sys.version_info >= (3,):
    class MetavarTypeHelpFormatter(HelpFormatter): ...

class Action:
    def __init__(self,
                 option_strings: Sequence[str],
                 dest: str = ...,
                 nargs: Optional[Union[int, str]] = ...,
                 const: Any = ...,
                 default: Any = ...,
                 type: Union[Callable[[str], _T], FileType, None] = ...,
                 choices: Optional[Iterable[_T]] = ...,
                 required: bool = ...,
                 help: Optional[str] = ...,
                 metavar: Union[str, Tuple[str, ...]] = ...) -> None: ...
    def __call__(self, parser: ArgumentParser, namespace: Namespace,
                 values: Union[str, Sequence[Any], None],
                 option_string: str = ...) -> None: ...

class Namespace:
    def __getattr__(self, name: str) -> Any: ...
    def __setattr__(self, name: str, value: Any) -> None: ...

class FileType:
    if sys.version_info >= (3, 4):
        def __init__(self, mode: str = ..., bufsize: int = ...,
                     encoding: Optional[str] = ...,
                     errors: Optional[str] = ...) -> None: ...
    elif sys.version_info >= (3,):
        def __init__(self,
                     mode: str = ..., bufsize: int = ...) -> None: ...
    else:
        def __init__(self,
                     mode: str = ..., bufsize: Optional[int] = ...) -> None: ...
    def __call__(self, string: str) -> IO[Any]: ...

class _ArgumentGroup:
    def add_argument(self,
                     *name_or_flags: Union[str, Sequence[str]],
                     action: Union[str, Type[Action]] = ...,
                     nargs: Union[int, str] = ...,
                     const: Any = ...,
                     default: Any = ...,
                     type: Union[Callable[[str], _T], FileType] = ...,
                     choices: Iterable[_T] = ...,
                     required: bool = ...,
                     help: str = ...,
                     metavar: Union[str, Tuple[str, ...]] = ...,
                     dest: str = ...,
                     version: str = ...) -> None: ...
    def add_mutually_exclusive_group(self, required: bool = ...) -> _MutuallyExclusiveGroup: ...

class _MutuallyExclusiveGroup(_ArgumentGroup): ...

class _SubParsersAction:
    # TODO: Type keyword args properly.
    def add_parser(self, name: str, **kwargs: Any) -> ArgumentParser: ...

# not documented
class ArgumentTypeError(Exception): ...
