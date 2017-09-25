# Stubs for argparse (Python 3.4)

from typing import (
    Any, Callable, Iterable, List, IO, Optional, Sequence, Tuple, Type, Union,
    TypeVar, overload
)
import sys

_T = TypeVar('_T')

if sys.version_info >= (3,):
    _Text = str
else:
    _Text = Union[str, unicode]

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
                     prefix_chars: _Text = ...,
                     fromfile_prefix_chars: Optional[str] = ...,
                     argument_default: Optional[str] = ...,
                     conflict_handler: _Text = ...,
                     add_help: bool = ...,
                     allow_abbrev: bool = ...) -> None: ...
    else:
        def __init__(self,
                     prog: Optional[_Text] = ...,
                     usage: Optional[_Text] = ...,
                     description: Optional[_Text] = ...,
                     epilog: Optional[_Text] = ...,
                     parents: Sequence[ArgumentParser] = ...,
                     formatter_class: Type[HelpFormatter] = ...,
                     prefix_chars: _Text = ...,
                     fromfile_prefix_chars: Optional[_Text] = ...,
                     argument_default: Optional[_Text] = ...,
                     conflict_handler: _Text = ...,
                     add_help: bool = ...) -> None: ...
    def add_argument(self,
                     *name_or_flags: Union[_Text, Sequence[_Text]],
                     action: Union[_Text, Type[Action]] = ...,
                     nargs: Union[int, _Text] = ...,
                     const: Any = ...,
                     default: Any = ...,
                     type: Union[Callable[[str], _T], FileType] = ...,
                     choices: Iterable[_T] = ...,
                     required: bool = ...,
                     help: _Text = ...,
                     metavar: Union[_Text, Tuple[_Text, ...]] = ...,
                     dest: _Text = ...,
                     version: _Text = ...) -> None: ...  # weirdly documented
    def parse_args(self, args: Optional[Sequence[_Text]] = ...,
                   namespace: Optional[Namespace] = ...) -> Namespace: ...
    def add_subparsers(self, title: _Text = ...,
                       description: Optional[_Text] = ...,
                       prog: _Text = ...,
                       parser_class: Type[ArgumentParser] = ...,
                       action: Type[Action] = ...,
                       option_string: _Text = ...,
                       dest: Optional[_Text] = ...,
                       help: Optional[_Text] = ...,
                       metavar: Optional[_Text] = ...) -> _SubParsersAction: ...
    def add_argument_group(self, title: Optional[_Text] = ...,
                           description: Optional[_Text] = ...) -> _ArgumentGroup: ...
    def add_mutually_exclusive_group(self, required: bool = ...) -> _MutuallyExclusiveGroup: ...
    def set_defaults(self, **kwargs: Any) -> None: ...
    def get_default(self, dest: _Text) -> Any: ...
    def print_usage(self, file: Optional[IO[str]] = ...) -> None: ...
    def print_help(self, file: Optional[IO[str]] = ...) -> None: ...
    def format_usage(self) -> str: ...
    def format_help(self) -> str: ...
    def parse_known_args(self, args: Optional[Sequence[_Text]] = ...,
                         namespace: Optional[Namespace] = ...) -> Tuple[Namespace, List[str]]: ...
    def convert_arg_line_to_args(self, arg_line: _Text) -> List[str]: ...
    def exit(self, status: int = ..., message: Optional[_Text] = ...) -> None: ...
    def error(self, message: _Text) -> None: ...

class HelpFormatter:
    # not documented
    def __init__(self, prog: _Text, indent_increment: int = ...,
                 max_help_position: int = ...,
                 width: Optional[int] = ...) -> None: ...
class RawDescriptionHelpFormatter(HelpFormatter): ...
class RawTextHelpFormatter(HelpFormatter): ...
class ArgumentDefaultsHelpFormatter(HelpFormatter): ...
if sys.version_info >= (3,):
    class MetavarTypeHelpFormatter(HelpFormatter): ...

class Action:
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text = ...,
                 nargs: Optional[Union[int, _Text]] = ...,
                 const: Any = ...,
                 default: Any = ...,
                 type: Union[Callable[[str], _T], FileType, None] = ...,
                 choices: Optional[Iterable[_T]] = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...,
                 metavar: Union[_Text, Tuple[_Text, ...]] = ...) -> None: ...
    def __call__(self, parser: ArgumentParser, namespace: Namespace,
                 values: Union[_Text, Sequence[Any], None],
                 option_string: _Text = ...) -> None: ...

class Namespace:
    def __init__(self, **kwargs: Any) -> None: ...
    def __getattr__(self, name: _Text) -> Any: ...
    def __setattr__(self, name: _Text, value: Any) -> None: ...
    def __contains__(self, key: str) -> bool: ...

class FileType:
    if sys.version_info >= (3, 4):
        def __init__(self, mode: _Text = ..., bufsize: int = ...,
                     encoding: Optional[_Text] = ...,
                     errors: Optional[_Text] = ...) -> None: ...
    elif sys.version_info >= (3,):
        def __init__(self,
                     mode: _Text = ..., bufsize: int = ...) -> None: ...
    else:
        def __init__(self,
                     mode: _Text = ..., bufsize: Optional[int] = ...) -> None: ...
    def __call__(self, string: _Text) -> IO[Any]: ...

class _ArgumentGroup:
    def add_argument(self,
                     *name_or_flags: Union[_Text, Sequence[_Text]],
                     action: Union[_Text, Type[Action]] = ...,
                     nargs: Union[int, _Text] = ...,
                     const: Any = ...,
                     default: Any = ...,
                     type: Union[Callable[[str], _T], FileType] = ...,
                     choices: Iterable[_T] = ...,
                     required: bool = ...,
                     help: _Text = ...,
                     metavar: Union[_Text, Tuple[_Text, ...]] = ...,
                     dest: _Text = ...,
                     version: _Text = ...) -> None: ...
    def add_mutually_exclusive_group(self, required: bool = ...) -> _MutuallyExclusiveGroup: ...

class _MutuallyExclusiveGroup(_ArgumentGroup): ...

class _SubParsersAction:
    # TODO: Type keyword args properly.
    def add_parser(self, name: _Text, **kwargs: Any) -> ArgumentParser: ...

# not documented
class ArgumentTypeError(Exception): ...
