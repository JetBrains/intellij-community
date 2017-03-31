from distutils.version import Version
from typing import Any, Callable, Dict, List, TypeVar, Union

from click.core import Command, Group, Argument, Option, Parameter, Context
from click.types import ParamType

_T = TypeVar('_T')
_Decorator = Callable[[_T], _T]


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
    name: str = None,
    cls: type = Command,
    # Command
    help: str = None,
    epilog: str = None,
    short_help: str = None,
    options_metavar: str = '[OPTIONS]',
    add_help_option: bool = True,
) -> _Decorator:
    ...


# This inherits attrs from Group, MultiCommand and Command.

def group(
    name: str = None,
    cls: type = Group,
    # Group
    commands: Dict[str, Command] = None,
    # MultiCommand
    invoke_without_command: bool = False,
    no_args_is_help: bool = None,
    subcommand_metavar: str = None,
    chain: bool = False,
    result_callback: Callable = None,
    # Command
    help: str = None,
    epilog: str = None,
    short_help: str = None,
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
    required: bool = None,
    # Parameter
    type: Union[type, ParamType] = None,
    default: Any = None,
    callback: Callable[[Context, Parameter, str], Any] = None,
    nargs: int = None,
    metavar: str = None,
    expose_value: bool = True,
    is_eager: bool = False,
    envvar: Union[str, List[str]] = None
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
    is_flag: bool = None,
    flag_value: Any = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Union[type, ParamType] = None,
    help: str = None,
    # Parameter
    default: Any = None,
    callback: Callable[[Context, Parameter, str], Any] = None,
    nargs: int = None,
    metavar: str = None,
    expose_value: bool = True,
    is_eager: bool = False,
    envvar: Union[str, List[str]] = None
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
    flag_value: Any = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Union[type, ParamType] = None,
    help: str = 'Confirm the action without prompting.',
    # Parameter
    default: Any = None,
    callback: Callable[[Context, Parameter, str], Any] = None,
    nargs: int = None,
    metavar: str = None,
    expose_value: bool = False,
    is_eager: bool = False,
    envvar: Union[str, List[str]] = None
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
    is_flag: bool = None,
    flag_value: Any = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Union[type, ParamType] = None,
    help: str = None,
    # Parameter
    default: Any = None,
    callback: Callable[[Context, Parameter, str], Any] = None,
    nargs: int = None,
    metavar: str = None,
    expose_value: bool = True,
    is_eager: bool = False,
    envvar: Union[str, List[str]] = None
) -> _Decorator:
    ...


# Defaults copied from the decorator body.
def version_option(
    version: Union[str, Version] = None,
    *param_decls: str,
    cls: type = Option,
    # Option
    prog_name: str = None,
    show_default: bool = False,
    prompt: bool = False,
    confirmation_prompt: bool = False,
    hide_input: bool = False,
    is_flag: bool = True,
    flag_value: Any = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Union[type, ParamType] = None,
    help: str = 'Show the version and exit.',
    # Parameter
    default: Any = None,
    callback: Callable[[Context, Parameter, str], Any] = None,
    nargs: int = None,
    metavar: str = None,
    expose_value: bool = False,
    is_eager: bool = True,
    envvar: Union[str, List[str]] = None
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
    flag_value: Any = None,
    multiple: bool = False,
    count: bool = False,
    allow_from_autoenv: bool = True,
    type: Union[type, ParamType] = None,
    help: str = 'Show this message and exit.',
    # Parameter
    default: Any = None,
    callback: Callable[[Context, Parameter, str], Any] = None,
    nargs: int = None,
    metavar: str = None,
    expose_value: bool = False,
    is_eager: bool = True,
    envvar: Union[str, List[str]] = None
) -> _Decorator:
    ...
