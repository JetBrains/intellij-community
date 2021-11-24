from _typeshed import IdentityFunction
from distutils.version import Version
from typing import Any, Callable, Iterable, Text, Type, TypeVar, Union, overload

from click.core import Argument, Command, Context, Group, Option, Parameter, _ConvertibleType

_T = TypeVar("_T")
_F = TypeVar("_F", bound=Callable[..., Any])

_Callback = Callable[[Context, Union[Option, Parameter], Any], Any]

def pass_context(__f: _T) -> _T: ...
def pass_obj(__f: _T) -> _T: ...
def make_pass_decorator(object_type: type, ensure: bool = ...) -> IdentityFunction: ...

# NOTE: Decorators below have **attrs converted to concrete constructor
# arguments from core.pyi to help with type checking.

def command(
    name: str | None = ...,
    cls: Type[Command] | None = ...,
    # Command
    context_settings: dict[Any, Any] | None = ...,
    help: str | None = ...,
    epilog: str | None = ...,
    short_help: str | None = ...,
    options_metavar: str = ...,
    add_help_option: bool = ...,
    no_args_is_help: bool = ...,
    hidden: bool = ...,
    deprecated: bool = ...,
) -> Callable[[Callable[..., Any]], Command]: ...

# This inherits attrs from Group, MultiCommand and Command.

def group(
    name: str | None = ...,
    cls: Type[Command] = ...,
    # Group
    commands: dict[str, Command] | None = ...,
    # MultiCommand
    invoke_without_command: bool = ...,
    no_args_is_help: bool | None = ...,
    subcommand_metavar: str | None = ...,
    chain: bool = ...,
    result_callback: Callable[..., Any] | None = ...,
    # Command
    help: str | None = ...,
    epilog: str | None = ...,
    short_help: str | None = ...,
    options_metavar: str = ...,
    add_help_option: bool = ...,
    hidden: bool = ...,
    deprecated: bool = ...,
    # User-defined
    **kwargs: Any,
) -> Callable[[Callable[..., Any]], Group]: ...
def argument(
    *param_decls: Text,
    cls: Type[Argument] = ...,
    # Argument
    required: bool | None = ...,
    # Parameter
    type: _ConvertibleType | None = ...,
    default: Any | None = ...,
    callback: _Callback | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
    autocompletion: Callable[[Context, list[str], str], Iterable[str | tuple[str, str]]] | None = ...,
) -> IdentityFunction: ...
@overload
def option(
    *param_decls: Text,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool | None = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: _ConvertibleType | None = ...,
    help: Text | None = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    required: bool = ...,
    callback: _Callback | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
    # User-defined
    **kwargs: Any,
) -> IdentityFunction: ...
@overload
def option(
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool | None = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: _T = ...,
    help: str | None = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    required: bool = ...,
    callback: Callable[[Context, Option | Parameter, bool | int | str], _T] | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
    # User-defined
    **kwargs: Any,
) -> IdentityFunction: ...
@overload
def option(
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool | None = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: Type[str] = ...,
    help: str | None = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    required: bool = ...,
    callback: Callable[[Context, Option | Parameter, str], Any] = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
    # User-defined
    **kwargs: Any,
) -> IdentityFunction: ...
@overload
def option(
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool | None = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: Type[int] = ...,
    help: str | None = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    required: bool = ...,
    callback: Callable[[Context, Option | Parameter, int], Any] = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
    # User-defined
    **kwargs: Any,
) -> IdentityFunction: ...
def confirmation_option(
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: _ConvertibleType | None = ...,
    help: str = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    callback: _Callback | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
) -> IdentityFunction: ...
def password_option(
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool | None = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: _ConvertibleType | None = ...,
    help: str | None = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    callback: _Callback | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
) -> IdentityFunction: ...
def version_option(
    version: str | Version | None = ...,
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    prog_name: str | None = ...,
    message: str | None = ...,
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: _ConvertibleType | None = ...,
    help: str = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    callback: _Callback | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
) -> IdentityFunction: ...
def help_option(
    *param_decls: str,
    cls: Type[Option] = ...,
    # Option
    show_default: bool | Text = ...,
    prompt: bool | Text = ...,
    confirmation_prompt: bool = ...,
    hide_input: bool = ...,
    is_flag: bool = ...,
    flag_value: Any | None = ...,
    multiple: bool = ...,
    count: bool = ...,
    allow_from_autoenv: bool = ...,
    type: _ConvertibleType | None = ...,
    help: str = ...,
    show_choices: bool = ...,
    # Parameter
    default: Any | None = ...,
    callback: _Callback | None = ...,
    nargs: int | None = ...,
    metavar: str | None = ...,
    expose_value: bool = ...,
    is_eager: bool = ...,
    envvar: str | list[str] | None = ...,
) -> IdentityFunction: ...
