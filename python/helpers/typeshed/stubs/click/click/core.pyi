from typing import Any, Callable, ContextManager, Iterable, Mapping, NoReturn, Optional, Sequence, Set, Tuple, TypeVar, Union

from click.formatting import HelpFormatter
from click.parser import OptionParser

_CC = TypeVar("_CC", bound=Callable[[], Any])

def invoke_param_callback(
    callback: Callable[[Context, Parameter, str | None], Any], ctx: Context, param: Parameter, value: str | None
) -> Any: ...
def augment_usage_errors(ctx: Context, param: Parameter | None = ...) -> ContextManager[None]: ...
def iter_params_for_processing(
    invocation_order: Sequence[Parameter], declaration_order: Iterable[Parameter]
) -> Iterable[Parameter]: ...

class Context:
    parent: Context | None
    command: Command
    info_name: str | None
    params: dict[Any, Any]
    args: list[str]
    protected_args: list[str]
    obj: Any
    default_map: Mapping[str, Any] | None
    invoked_subcommand: str | None
    terminal_width: int | None
    max_content_width: int | None
    allow_extra_args: bool
    allow_interspersed_args: bool
    ignore_unknown_options: bool
    help_option_names: list[str]
    token_normalize_func: Callable[[str], str] | None
    resilient_parsing: bool
    auto_envvar_prefix: str | None
    color: bool | None
    _meta: dict[str, Any]
    _close_callbacks: list[Any]
    _depth: int
    def __init__(
        self,
        command: Command,
        parent: Context | None = ...,
        info_name: str | None = ...,
        obj: Any | None = ...,
        auto_envvar_prefix: str | None = ...,
        default_map: Mapping[str, Any] | None = ...,
        terminal_width: int | None = ...,
        max_content_width: int | None = ...,
        resilient_parsing: bool = ...,
        allow_extra_args: bool | None = ...,
        allow_interspersed_args: bool | None = ...,
        ignore_unknown_options: bool | None = ...,
        help_option_names: list[str] | None = ...,
        token_normalize_func: Callable[[str], str] | None = ...,
        color: bool | None = ...,
    ) -> None: ...
    @property
    def meta(self) -> dict[str, Any]: ...
    @property
    def command_path(self) -> str: ...
    def scope(self, cleanup: bool = ...) -> ContextManager[Context]: ...
    def make_formatter(self) -> HelpFormatter: ...
    def call_on_close(self, f: _CC) -> _CC: ...
    def close(self) -> None: ...
    def find_root(self) -> Context: ...
    def find_object(self, object_type: type) -> Any: ...
    def ensure_object(self, object_type: type) -> Any: ...
    def lookup_default(self, name: str) -> Any: ...
    def fail(self, message: str) -> NoReturn: ...
    def abort(self) -> NoReturn: ...
    def exit(self, code: int | str = ...) -> NoReturn: ...
    def get_usage(self) -> str: ...
    def get_help(self) -> str: ...
    def invoke(self, callback: Command | Callable[..., Any], *args: Any, **kwargs: Any) -> Any: ...
    def forward(self, callback: Command | Callable[..., Any], *args: Any, **kwargs: Any) -> Any: ...

class BaseCommand:
    allow_extra_args: bool
    allow_interspersed_args: bool
    ignore_unknown_options: bool
    name: str
    context_settings: dict[Any, Any]
    def __init__(self, name: str, context_settings: dict[Any, Any] | None = ...) -> None: ...
    def get_usage(self, ctx: Context) -> str: ...
    def get_help(self, ctx: Context) -> str: ...
    def make_context(self, info_name: str, args: list[str], parent: Context | None = ..., **extra: Any) -> Context: ...
    def parse_args(self, ctx: Context, args: list[str]) -> list[str]: ...
    def invoke(self, ctx: Context) -> Any: ...
    def main(
        self,
        args: list[str] | None = ...,
        prog_name: str | None = ...,
        complete_var: str | None = ...,
        standalone_mode: bool = ...,
        **extra: Any,
    ) -> Any: ...
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...

class Command(BaseCommand):
    callback: Callable[..., Any] | None
    params: list[Parameter]
    help: str | None
    epilog: str | None
    short_help: str | None
    options_metavar: str
    add_help_option: bool
    no_args_is_help: bool
    hidden: bool
    deprecated: bool
    def __init__(
        self,
        name: str,
        context_settings: dict[Any, Any] | None = ...,
        callback: Callable[..., Any] | None = ...,
        params: list[Parameter] | None = ...,
        help: str | None = ...,
        epilog: str | None = ...,
        short_help: str | None = ...,
        options_metavar: str = ...,
        add_help_option: bool = ...,
        no_args_is_help: bool = ...,
        hidden: bool = ...,
        deprecated: bool = ...,
    ) -> None: ...
    def get_params(self, ctx: Context) -> list[Parameter]: ...
    def format_usage(self, ctx: Context, formatter: HelpFormatter) -> None: ...
    def collect_usage_pieces(self, ctx: Context) -> list[str]: ...
    def get_help_option_names(self, ctx: Context) -> Set[str]: ...
    def get_help_option(self, ctx: Context) -> Option | None: ...
    def make_parser(self, ctx: Context) -> OptionParser: ...
    def get_short_help_str(self, limit: int = ...) -> str: ...
    def format_help(self, ctx: Context, formatter: HelpFormatter) -> None: ...
    def format_help_text(self, ctx: Context, formatter: HelpFormatter) -> None: ...
    def format_options(self, ctx: Context, formatter: HelpFormatter) -> None: ...
    def format_epilog(self, ctx: Context, formatter: HelpFormatter) -> None: ...

_T = TypeVar("_T")
_F = TypeVar("_F", bound=Callable[..., Any])

class MultiCommand(Command):
    no_args_is_help: bool
    invoke_without_command: bool
    subcommand_metavar: str
    chain: bool
    result_callback: Callable[..., Any]
    def __init__(
        self,
        name: str | None = ...,
        invoke_without_command: bool = ...,
        no_args_is_help: bool | None = ...,
        subcommand_metavar: str | None = ...,
        chain: bool = ...,
        result_callback: Callable[..., Any] | None = ...,
        **attrs: Any,
    ) -> None: ...
    def resultcallback(self, replace: bool = ...) -> Callable[[_F], _F]: ...
    def format_commands(self, ctx: Context, formatter: HelpFormatter) -> None: ...
    def resolve_command(self, ctx: Context, args: list[str]) -> Tuple[str, Command, list[str]]: ...
    def get_command(self, ctx: Context, cmd_name: str) -> Command | None: ...
    def list_commands(self, ctx: Context) -> Iterable[str]: ...

class Group(MultiCommand):
    commands: dict[str, Command]
    def __init__(self, name: str | None = ..., commands: dict[str, Command] | None = ..., **attrs: Any) -> None: ...
    def add_command(self, cmd: Command, name: str | None = ...) -> None: ...
    def command(self, *args: Any, **kwargs: Any) -> Callable[[Callable[..., Any]], Command]: ...
    def group(self, *args: Any, **kwargs: Any) -> Callable[[Callable[..., Any]], Group]: ...

class CommandCollection(MultiCommand):
    sources: list[MultiCommand]
    def __init__(self, name: str | None = ..., sources: list[MultiCommand] | None = ..., **attrs: Any) -> None: ...
    def add_source(self, multi_cmd: MultiCommand) -> None: ...

class _ParamType:
    name: str
    is_composite: bool
    envvar_list_splitter: str | None
    def __call__(self, value: str | None, param: Parameter | None = ..., ctx: Context | None = ...) -> Any: ...
    def get_metavar(self, param: Parameter) -> str: ...
    def get_missing_message(self, param: Parameter) -> str: ...
    def convert(self, value: str, param: Parameter | None, ctx: Context | None) -> Any: ...
    def split_envvar_value(self, rv: str) -> list[str]: ...
    def fail(self, message: str, param: Parameter | None = ..., ctx: Context | None = ...) -> NoReturn: ...

# This type is here to resolve https://github.com/python/mypy/issues/5275
_ConvertibleType = Union[
    type, _ParamType, Tuple[Union[type, _ParamType], ...], Callable[[str], Any], Callable[[Optional[str]], Any]
]

class Parameter:
    param_type_name: str
    name: str
    opts: list[str]
    secondary_opts: list[str]
    type: _ParamType
    required: bool
    callback: Callable[[Context, Parameter, str], Any] | None
    nargs: int
    multiple: bool
    expose_value: bool
    default: Any
    is_eager: bool
    metavar: str | None
    envvar: str | list[str] | None
    def __init__(
        self,
        param_decls: Iterable[str] | None = ...,
        type: _ConvertibleType | None = ...,
        required: bool = ...,
        default: Any | None = ...,
        callback: Callable[[Context, Parameter, str], Any] | None = ...,
        nargs: int | None = ...,
        metavar: str | None = ...,
        expose_value: bool = ...,
        is_eager: bool = ...,
        envvar: str | list[str] | None = ...,
    ) -> None: ...
    @property
    def human_readable_name(self) -> str: ...
    def make_metavar(self) -> str: ...
    def get_default(self, ctx: Context) -> Any: ...
    def add_to_parser(self, parser: OptionParser, ctx: Context) -> None: ...
    def consume_value(self, ctx: Context, opts: dict[str, Any]) -> Any: ...
    def type_cast_value(self, ctx: Context, value: Any) -> Any: ...
    def process_value(self, ctx: Context, value: Any) -> Any: ...
    def value_is_missing(self, value: Any) -> bool: ...
    def full_process_value(self, ctx: Context, value: Any) -> Any: ...
    def resolve_envvar_value(self, ctx: Context) -> str: ...
    def value_from_envvar(self, ctx: Context) -> str | list[str]: ...
    def handle_parse_result(self, ctx: Context, opts: dict[str, Any], args: list[str]) -> Tuple[Any, list[str]]: ...
    def get_help_record(self, ctx: Context) -> Tuple[str, str]: ...
    def get_usage_pieces(self, ctx: Context) -> list[str]: ...
    def get_error_hint(self, ctx: Context) -> str: ...

class Option(Parameter):
    prompt: str  # sic
    confirmation_prompt: bool
    hide_input: bool
    is_flag: bool
    flag_value: Any
    is_bool_flag: bool
    count: bool
    multiple: bool
    allow_from_autoenv: bool
    help: str | None
    hidden: bool
    show_default: bool
    show_choices: bool
    show_envvar: bool
    def __init__(
        self,
        param_decls: Iterable[str] | None = ...,
        show_default: bool = ...,
        prompt: bool | str = ...,
        confirmation_prompt: bool = ...,
        hide_input: bool = ...,
        is_flag: bool | None = ...,
        flag_value: Any | None = ...,
        multiple: bool = ...,
        count: bool = ...,
        allow_from_autoenv: bool = ...,
        type: _ConvertibleType | None = ...,
        help: str | None = ...,
        hidden: bool = ...,
        show_choices: bool = ...,
        show_envvar: bool = ...,
        **attrs: Any,
    ) -> None: ...
    def prompt_for_value(self, ctx: Context) -> Any: ...

class Argument(Parameter):
    def __init__(self, param_decls: Iterable[str] | None = ..., required: bool | None = ..., **attrs: Any) -> None: ...
