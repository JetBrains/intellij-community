# Stubs for argparse (Python 2.7 and 3.4)

from typing import (
    Any, Callable, Dict, Generator, Iterable, List, IO, NoReturn, Optional,
    Pattern, Sequence, Tuple, Type, Union, TypeVar, overload
)
import sys

_T = TypeVar('_T')
_ActionT = TypeVar('_ActionT', bound='Action')
_N = TypeVar('_N')

if sys.version_info >= (3,):
    _Text = str
else:
    _Text = Union[str, unicode]

ONE_OR_MORE: str
OPTIONAL: str
PARSER: str
REMAINDER: str
SUPPRESS: str
ZERO_OR_MORE: str
_UNRECOGNIZED_ARGS_ATTR: str  # undocumented

class ArgumentError(Exception): ...

# undocumented
class _AttributeHolder:
    def _get_kwargs(self) -> List[Tuple[str, Any]]: ...
    def _get_args(self) -> List[Any]: ...

# undocumented
class _ActionsContainer:
    description: Optional[_Text]
    prefix_chars: _Text
    argument_default: Optional[_Text]
    conflict_handler: _Text

    _registries: Dict[_Text, Dict[Any, Any]]
    _actions: List[Action]
    _option_string_actions: Dict[_Text, Action]
    _action_groups: List[_ArgumentGroup]
    _mutually_exclusive_groups: List[_MutuallyExclusiveGroup]
    _defaults: Dict[str, Any]
    _negative_number_matcher: Pattern[str]
    _has_negative_number_optionals: List[bool]

    def __init__(self, description: Optional[_Text], prefix_chars: _Text,
                 argument_default: Optional[_Text], conflict_handler: _Text) -> None: ...
    def register(self, registry_name: _Text, value: Any, object: Any) -> None: ...
    def _registry_get(self, registry_name: _Text, value: Any, default: Any = ...) -> Any: ...
    def set_defaults(self, **kwargs: Any) -> None: ...
    def get_default(self, dest: _Text) -> Any: ...
    def add_argument(self,
                     *name_or_flags: _Text,
                     action: Union[_Text, Type[Action]] = ...,
                     nargs: Union[int, _Text] = ...,
                     const: Any = ...,
                     default: Any = ...,
                     type: Union[Callable[[str], _T], FileType] = ...,
                     choices: Iterable[_T] = ...,
                     required: bool = ...,
                     help: Optional[_Text] = ...,
                     metavar: Optional[Union[_Text, Tuple[_Text, ...]]] = ...,
                     dest: Optional[_Text] = ...,
                     version: _Text = ...,
                     **kwargs: Any) -> Action: ...
    def add_argument_group(self, *args: Any, **kwargs: Any) -> _ArgumentGroup: ...
    def add_mutually_exclusive_group(self, **kwargs: Any) -> _MutuallyExclusiveGroup: ...
    def _add_action(self, action: _ActionT) -> _ActionT: ...
    def _remove_action(self, action: Action) -> None: ...
    def _add_container_actions(self, container: _ActionsContainer) -> None: ...
    def _get_positional_kwargs(self, dest: _Text, **kwargs: Any) -> Dict[str, Any]: ...
    def _get_optional_kwargs(self, *args: Any, **kwargs: Any) -> Dict[str, Any]: ...
    def _pop_action_class(self, kwargs: Any, default: Optional[Type[Action]] = ...) -> Type[Action]: ...
    def _get_handler(self) -> Callable[[Action, Iterable[Tuple[_Text, Action]]], Any]: ...
    def _check_conflict(self, action: Action) -> None: ...
    def _handle_conflict_error(self, action: Action, conflicting_actions: Iterable[Tuple[_Text, Action]]) -> NoReturn: ...
    def _handle_conflict_resolve(self, action: Action, conflicting_actions: Iterable[Tuple[_Text, Action]]) -> None: ...

class ArgumentParser(_AttributeHolder, _ActionsContainer):
    prog: _Text
    usage: Optional[_Text]
    epilog: Optional[_Text]
    formatter_class: Type[HelpFormatter]
    fromfile_prefix_chars: Optional[_Text]
    add_help: bool

    if sys.version_info >= (3, 5):
        allow_abbrev: bool

    # undocumented
    _positionals: _ArgumentGroup
    _optionals: _ArgumentGroup
    _subparsers: Optional[_ArgumentGroup]

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

    # The type-ignores in these overloads should be temporary.  See:
    # https://github.com/python/typeshed/pull/2643#issuecomment-442280277
    @overload
    def parse_args(self, args: Optional[Sequence[_Text]] = ...) -> Namespace: ...
    @overload
    def parse_args(self, args: Optional[Sequence[_Text]], namespace: None) -> Namespace: ...  # type: ignore
    @overload
    def parse_args(self, args: Optional[Sequence[_Text]], namespace: _N) -> _N: ...
    @overload
    def parse_args(self, *, namespace: None) -> Namespace: ...  # type: ignore
    @overload
    def parse_args(self, *, namespace: _N) -> _N: ...

    if sys.version_info >= (3, 7):
        def add_subparsers(self, title: _Text = ...,
                           description: Optional[_Text] = ...,
                           prog: _Text = ...,
                           parser_class: Type[ArgumentParser] = ...,
                           action: Type[Action] = ...,
                           option_string: _Text = ...,
                           dest: Optional[_Text] = ...,
                           required: bool = ...,
                           help: Optional[_Text] = ...,
                           metavar: Optional[_Text] = ...) -> _SubParsersAction: ...
    else:
        def add_subparsers(self, title: _Text = ...,
                           description: Optional[_Text] = ...,
                           prog: _Text = ...,
                           parser_class: Type[ArgumentParser] = ...,
                           action: Type[Action] = ...,
                           option_string: _Text = ...,
                           dest: Optional[_Text] = ...,
                           help: Optional[_Text] = ...,
                           metavar: Optional[_Text] = ...) -> _SubParsersAction: ...

    def print_usage(self, file: Optional[IO[str]] = ...) -> None: ...
    def print_help(self, file: Optional[IO[str]] = ...) -> None: ...
    def format_usage(self) -> str: ...
    def format_help(self) -> str: ...
    def parse_known_args(self, args: Optional[Sequence[_Text]] = ...,
                         namespace: Optional[Namespace] = ...) -> Tuple[Namespace, List[str]]: ...
    def convert_arg_line_to_args(self, arg_line: _Text) -> List[str]: ...
    def exit(self, status: int = ..., message: Optional[_Text] = ...) -> NoReturn: ...
    def error(self, message: _Text) -> NoReturn: ...
    if sys.version_info >= (3, 7):
        def parse_intermixed_args(self, args: Optional[Sequence[_Text]] = ...,
                                  namespace: Optional[Namespace] = ...) -> Namespace: ...
        def parse_known_intermixed_args(self,
                                        args: Optional[Sequence[_Text]] = ...,
                                        namespace: Optional[Namespace] = ...) -> Tuple[Namespace, List[str]]: ...
    # undocumented
    def _get_optional_actions(self) -> List[Action]: ...
    def _get_positional_actions(self) -> List[Action]: ...
    def _parse_known_args(self, arg_strings: List[_Text], namespace: Namespace) -> Tuple[Namespace, List[str]]: ...
    def _read_args_from_files(self, arg_strings: List[_Text]) -> List[_Text]: ...
    def _match_argument(self, action: Action, arg_strings_pattern: _Text) -> int: ...
    def _match_arguments_partial(self, actions: Sequence[Action], arg_strings_pattern: _Text) -> List[int]: ...
    def _parse_optional(self, arg_string: _Text) -> Optional[Tuple[Optional[Action], _Text, Optional[_Text]]]: ...
    def _get_option_tuples(self, option_string: _Text) -> List[Tuple[Action, _Text, Optional[_Text]]]: ...
    def _get_nargs_pattern(self, action: Action) -> _Text: ...
    def _get_values(self, action: Action, arg_strings: List[_Text]) -> Any: ...
    def _get_value(self, action: Action, arg_string: _Text) -> Any: ...
    def _check_value(self, action: Action, value: Any) -> None: ...
    def _get_formatter(self) -> HelpFormatter: ...
    def _print_message(self, message: str, file: Optional[IO[str]] = ...) -> None: ...

class HelpFormatter:
    # undocumented
    _prog: _Text
    _indent_increment: int
    _max_help_position: int
    _width: int
    _current_indent: int
    _level: int
    _action_max_length: int
    _root_section: Any
    _current_section: Any
    _whitespace_matcher: Pattern[str]
    _long_break_matcher: Pattern[str]
    _Section: Type[Any]  # Nested class
    def __init__(self, prog: _Text, indent_increment: int = ...,
                 max_help_position: int = ...,
                 width: Optional[int] = ...) -> None: ...
    def _indent(self) -> None: ...
    def _dedent(self) -> None: ...
    def _add_item(self, func: Callable[..., _Text], args: Iterable[Any]) -> None: ...
    def start_section(self, heading: Optional[_Text]) -> None: ...
    def end_section(self) -> None: ...
    def add_text(self, text: Optional[_Text]) -> None: ...
    def add_usage(self, usage: _Text, actions: Iterable[Action], groups: Iterable[_ArgumentGroup], prefix: Optional[_Text] = ...) -> None: ...
    def add_argument(self, action: Action) -> None: ...
    def add_arguments(self, actions: Iterable[Action]) -> None: ...
    def format_help(self) -> _Text: ...
    def _join_parts(self, part_strings: Iterable[_Text]) -> _Text: ...
    def _format_usage(self, usage: _Text, actions: Iterable[Action], groups: Iterable[_ArgumentGroup], prefix: Optional[_Text]) -> _Text: ...
    def _format_actions_usage(self, actions: Iterable[Action], groups: Iterable[_ArgumentGroup]) -> _Text: ...
    def _format_text(self, text: _Text) -> _Text: ...
    def _format_action(self, action: Action) -> _Text: ...
    def _format_action_invocation(self, action: Action) -> _Text: ...
    def _metavar_formatter(self, action: Action, default_metavar: _Text) -> Callable[[int], Tuple[_Text, ...]]: ...
    def _format_args(self, action: Action, default_metavar: _Text) -> _Text: ...
    def _expand_help(self, action: Action) -> _Text: ...
    def _iter_indented_subactions(self, action: Action) -> Generator[Action, None, None]: ...
    def _split_lines(self, text: _Text, width: int) -> List[_Text]: ...
    def _fill_text(self, text: _Text, width: int, indent: int) -> _Text: ...
    def _get_help_string(self, action: Action) -> Optional[_Text]: ...
    def _get_default_metavar_for_optional(self, action: Action) -> _Text: ...
    def _get_default_metavar_for_positional(self, action: Action) -> _Text: ...

class RawDescriptionHelpFormatter(HelpFormatter): ...
class RawTextHelpFormatter(HelpFormatter): ...
class ArgumentDefaultsHelpFormatter(HelpFormatter): ...
if sys.version_info >= (3,):
    class MetavarTypeHelpFormatter(HelpFormatter): ...

class Action(_AttributeHolder):
    option_strings: Sequence[_Text]
    dest: _Text
    nargs: Optional[Union[int, _Text]]
    const: Any
    default: Any
    type: Union[Callable[[str], Any], FileType, None]
    choices: Optional[Iterable[Any]]
    required: bool
    help: Optional[_Text]
    metavar: Optional[Union[_Text, Tuple[_Text, ...]]]

    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text,
                 nargs: Optional[Union[int, _Text]] = ...,
                 const: Any = ...,
                 default: Any = ...,
                 type: Optional[Union[Callable[[str], _T], FileType]] = ...,
                 choices: Optional[Iterable[_T]] = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...,
                 metavar: Optional[Union[_Text, Tuple[_Text, ...]]] = ...) -> None: ...
    def __call__(self, parser: ArgumentParser, namespace: Namespace,
                 values: Union[_Text, Sequence[Any], None],
                 option_string: Optional[_Text] = ...) -> None: ...

class Namespace(_AttributeHolder):
    def __init__(self, **kwargs: Any) -> None: ...
    def __getattr__(self, name: _Text) -> Any: ...
    def __setattr__(self, name: _Text, value: Any) -> None: ...
    def __contains__(self, key: str) -> bool: ...

class FileType:
    # undocumented
    _mode: _Text
    _bufsize: int
    if sys.version_info >= (3, 4):
        _encoding: Optional[_Text]
        _errors: Optional[_Text]
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

# undocumented
class _ArgumentGroup(_ActionsContainer):
    title: Optional[_Text]
    _group_actions: List[Action]
    def __init__(self, container: _ActionsContainer,
                 title: Optional[_Text] = ...,
                 description: Optional[_Text] = ..., **kwargs: Any) -> None: ...

# undocumented
class _MutuallyExclusiveGroup(_ArgumentGroup):
    required: bool
    _container: _ActionsContainer
    def __init__(self, container: _ActionsContainer, required: bool = ...) -> None: ...

# undocumented
class _StoreAction(Action): ...

# undocumented
class _StoreConstAction(Action):
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text,
                 const: Any,
                 default: Any = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...,
                 metavar: Optional[Union[_Text, Tuple[_Text, ...]]] = ...) -> None: ...

# undocumented
class _StoreTrueAction(_StoreConstAction):
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text,
                 default: bool = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...) -> None: ...

# undocumented
class _StoreFalseAction(_StoreConstAction):
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text,
                 default: bool = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...) -> None: ...

# undocumented
class _AppendAction(Action): ...

# undocumented
class _AppendConstAction(Action):
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text,
                 const: Any,
                 default: Any = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...,
                 metavar: Optional[Union[_Text, Tuple[_Text, ...]]] = ...) -> None: ...

# undocumented
class _CountAction(Action):
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text,
                 default: Any = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...) -> None: ...

# undocumented
class _HelpAction(Action):
    def __init__(self,
                 option_strings: Sequence[_Text],
                 dest: _Text = ...,
                 default: _Text = ...,
                 help: Optional[_Text] = ...) -> None: ...

# undocumented
class _VersionAction(Action):
    version: Optional[_Text]
    def __init__(self,
                 option_strings: Sequence[_Text],
                 version: Optional[_Text] = ...,
                 dest: _Text = ...,
                 default: _Text = ...,
                 help: _Text = ...) -> None: ...

# undocumented
class _SubParsersAction(Action):
    _ChoicesPseudoAction: Type[Any]  # nested class
    _prog_prefix: _Text
    _parser_class: Type[ArgumentParser]
    _name_parser_map: Dict[_Text, ArgumentParser]
    choices: Dict[_Text, ArgumentParser]
    _choices_actions: List[Action]
    def __init__(self,
                 option_strings: Sequence[_Text],
                 prog: _Text,
                 parser_class: Type[ArgumentParser],
                 dest: _Text = ...,
                 required: bool = ...,
                 help: Optional[_Text] = ...,
                 metavar: Optional[Union[_Text, Tuple[_Text, ...]]] = ...) -> None: ...
    # TODO: Type keyword args properly.
    def add_parser(self, name: _Text, **kwargs: Any) -> ArgumentParser: ...
    def _get_subactions(self) -> List[Action]: ...

# undocumented
class ArgumentTypeError(Exception): ...

if sys.version_info < (3, 7):
    # undocumented
    def _ensure_value(namespace: Namespace, name: _Text, value: Any) -> Any: ...

# undocumented
def _get_action_name(argument: Optional[Action]) -> Optional[str]: ...
