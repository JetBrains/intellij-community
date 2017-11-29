from distutils.version import Version
from typing import Any, Callable, Dict, List, Optional, TypeVar, Union

from click.core import Command, Group, Argument, Option, Parameter, Context
from click.types import ParamType

_T = TypeVar('_T')
_Decorator = Callable[[_T], _T]

_Callback = Callable[
    [Context, Union[Option, Parameter], Union[bool, int, str]],
    Any
]

def pass_context(_T) -> _T:
    ...


def pass_obj(_T) -> _T:
    ...


def make_pass_decorator(
    object_type: type, ensure: bool = False
) -> Callable[[_T], _T]:
    ...


# NOTE: Decorators below have **attrs converted to concrete constructor
# arguments from core.pyi to help with type checking.

def command(
    name: Optional[str] = None,
    cls: type = Command,
    # Command
    context_settings: Optional[Dict] = ...,
    help: Optional[str] = None,
    epilog: Optional[str] = None,
    short_help: Optional[str] = None,
    options_metavar: str = '[OPTIONS]',
    add_help_option: bool = True,
) -> _Decorator:
    ...


# This inherits attrs from Group, MultiCommand and Command.

def group(
    name: Optional[str] = None,
    cls: type = Group,
    # Group
    commands: Optional[Dict[str, Command]] = None,
    # MultiCommand
    invoke_without_command: bool = False,
    no_args_is_help: Optional[bool] = None,
    subcommand_metavar: Optional[str] = None,
    chain: bool = False,
    result_callback: Optional[Callable] = None,
    # Command
    help: Optional[str] = None,
    epilog: Optional[str] = None,
    short_help: Optional[str] = None,
    options_metavar: str = '[OPTIONS]',
    add_help_option: bool = True,
    # User-defined
    **kwargs: Any,
) -> _Decorator:
    ...


def argument(
    *param_decls: str,
    cls: type = Argument,
    # Argument
    required: Optional[bool] = None,
    # Parameter
    type: Optional[Union[type, ParamType]] = None,
    default: Optional[Any] = None,
    callback: Optional[_Callback] = ...,
    nargs: Optional[int] = None,
    metavar: Optional[str] = None,
    expose_value: bool = True,
    is_eager: bool = False,
    envvar: Optional[Union[str, List[str]]] = None
) -> _Decorator:
    ...


def option(
    *param_decls: str,
    cls: type = Option,
    # Option
    show_default: bool = False,
    prompt: bool = False,
    confirmation_prompt: bool = False,
    hide_input: bool = False,
    is_flag: Optional[bool] = None,
    flag_value: Optional[Any] = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Optional[Union[type, ParamType]] = None,
    help: Optional[str] = None,
    # Parameter
    default: Optional[Any] = None,
    required: bool = False,
    callback: Optional[_Callback] = ...,
    nargs: Optional[int] = None,
    metavar: Optional[str] = None,
    expose_value: bool = True,
    is_eager: bool = False,
    envvar: Optional[Union[str, List[str]]] = None
) -> _Decorator:
    ...


# Defaults copied from the decorator body.
def confirmation_option(
    *param_decls: str,
    cls: type = Option,
    # Option
    show_default: bool = False,
    prompt: str = 'Do you want to continue?',
    confirmation_prompt: bool = False,
    hide_input: bool = False,
    is_flag: bool = True,
    flag_value: Optional[Any] = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Optional[Union[type, ParamType]] = None,
    help: str = 'Confirm the action without prompting.',
    # Parameter
    default: Optional[Any] = None,
    callback: Optional[_Callback] = ...,
    nargs: Optional[int] = None,
    metavar: Optional[str] = None,
    expose_value: bool = False,
    is_eager: bool = False,
    envvar: Optional[Union[str, List[str]]] = None
) -> _Decorator:
    ...


# Defaults copied from the decorator body.
def password_option(
    *param_decls: str,
    cls: type = Option,
    # Option
    show_default: bool = False,
    prompt: bool = True,
    confirmation_prompt: bool = True,
    hide_input: bool = True,
    is_flag: Optional[bool] = None,
    flag_value: Optional[Any] = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Optional[Union[type, ParamType]] = None,
    help: Optional[str] = None,
    # Parameter
    default: Optional[Any] = None,
    callback: Optional[_Callback] = ...,
    nargs: Optional[int] = None,
    metavar: Optional[str] = None,
    expose_value: bool = True,
    is_eager: bool = False,
    envvar: Optional[Union[str, List[str]]] = None
) -> _Decorator:
    ...


# Defaults copied from the decorator body.
def version_option(
    version: Optional[Union[str, Version]] = None,
    *param_decls: str,
    cls: type = Option,
    # Option
    prog_name: Optional[str] = None,
    show_default: bool = False,
    prompt: bool = False,
    confirmation_prompt: bool = False,
    hide_input: bool = False,
    is_flag: bool = True,
    flag_value: Optional[Any] = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Optional[Union[type, ParamType]] = None,
    help: str = 'Show the version and exit.',
    # Parameter
    default: Optional[Any] = None,
    callback: Optional[_Callback] = ...,
    nargs: Optional[int] = None,
    metavar: Optional[str] = None,
    expose_value: bool = False,
    is_eager: bool = True,
    envvar: Optional[Union[str, List[str]]] = None
) -> _Decorator:
    ...


# Defaults copied from the decorator body.
def help_option(
    *param_decls: str,
    cls: type = Option,
    # Option
    show_default: bool = False,
    prompt: bool = False,
    confirmation_prompt: bool = False,
    hide_input: bool = False,
    is_flag: bool = True,
    flag_value: Optional[Any] = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Optional[Union[type, ParamType]] = None,
    help: str = 'Show this message and exit.',
    # Parameter
    default: Optional[Any] = None,
    callback: Optional[_Callback] = ...,
    nargs: Optional[int] = None,
    metavar: Optional[str] = None,
    expose_value: bool = False,
    is_eager: bool = True,
    envvar: Optional[Union[str, List[str]]] = None
) -> _Decorator:
    ...
