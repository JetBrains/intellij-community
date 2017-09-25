from contextlib import contextmanager
from typing import (
    Any,
    Callable,
    Dict,
    Generator,
    Iterable,
    List,
    Mapping,
    Optional,
    Sequence,
    Set,
    Tuple,
    TypeVar,
    Union,
)

from click.formatting import HelpFormatter
from click.parser import OptionParser


def invoke_param_callback(
    callback: Callable[['Context', 'Parameter', Optional[str]], Any],
    ctx: 'Context',
    param: 'Parameter',
    value: Optional[str]
) -> Any:
    ...


@contextmanager
def augment_usage_errors(
    ctx: 'Context', param: Optional['Parameter'] = None
) -> Generator[None, None, None]:
    ...


def iter_params_for_processing(
    invocation_order: Sequence['Parameter'],
    declaration_order: Iterable['Parameter'],
) -> Iterable['Parameter']:
    ...


class Context:
    parent: Optional['Context']
    command: 'Command'
    info_name: Optional[str]
    params: Dict
    args: List[str]
    protected_args: List[str]
    obj: Any
    default_map: Mapping[str, Any]
    invoked_subcommand: Optional[str]
    terminal_width: Optional[int]
    max_content_width: Optional[int]
    allow_extra_args: bool
    allow_interspersed_args: bool
    ignore_unknown_options: bool
    help_option_names: List[str]
    token_normalize_func: Optional[Callable[[str], str]]
    resilient_parsing: bool
    auto_envvar_prefix: Optional[str]
    color: Optional[bool]
    _meta: Dict[str, Any]
    _close_callbacks: List
    _depth: int

    # properties
    meta: Dict[str, Any]
    command_path: str

    def __init__(
        self,
        command: 'Command',
        parent: Optional['Context'] = None,
        info_name: Optional[str] = None,
        obj: Optional[Any] = None,
        auto_envvar_prefix: Optional[str] = None,
        default_map: Optional[Mapping[str, Any]] = None,
        terminal_width: Optional[int] = None,
        max_content_width: Optional[int] = None,
        resilient_parsing: bool = False,
        allow_extra_args: Optional[bool] = None,
        allow_interspersed_args: Optional[bool] = None,
        ignore_unknown_options: Optional[bool] = None,
        help_option_names: Optional[List[str]] = None,
        token_normalize_func: Optional[Callable[[str], str]] = None,
        color: Optional[bool] = None
    ) -> None:
        ...

    @contextmanager
    def scope(self, cleanup: bool = True) -> Generator['Context', None, None]:
        ...

    def make_formatter(self) -> HelpFormatter:
        ...

    def call_on_close(self, f: Callable) -> Callable:
        ...

    def close(self) -> None:
        ...

    def find_root(self) -> 'Context':
        ...

    def find_object(self, object_type: type) -> Any:
        ...

    def ensure_object(self, object_type: type) -> Any:
        ...

    def lookup_default(self, name: str) -> Any:
        ...

    def fail(self, message: str) -> None:
        ...

    def abort(self) -> None:
        ...

    def exit(self, code: Union[int, str] = 0) -> None:
        ...

    def get_usage(self) -> str:
        ...

    def get_help(self) -> str:
        ...

    def invoke(
        self, callback: Union['Command', Callable], *args, **kwargs
    ) -> Any:
        ...

    def forward(
        self, callback: Union['Command', Callable], *args, **kwargs
    ) -> Any:
        ...

class BaseCommand:
    allow_extra_args: bool
    allow_interspersed_args: bool
    ignore_unknown_options: bool
    name: str
    context_settings: Dict

    def __init__(self, name: str, context_settings: Optional[Dict] = None) -> None:
        ...

    def get_usage(self, ctx: Context) -> str:
        ...

    def get_help(self, ctx: Context) -> str:
        ...

    def make_context(
        self, info_name: str, args: List[str], parent: Optional[Context] = None, **extra
    ) -> Context:
        ...

    def parse_args(self, ctx: Context, args: List[str]) -> List[str]:
        ...

    def invoke(self, ctx: Context) -> Any:
        ...

    def main(
        self,
        args: Optional[List[str]] = None,
        prog_name: Optional[str] = None,
        complete_var: Optional[str] = None,
        standalone_mode: bool = True,
        **extra
    ) -> Any:
        ...

    def __call__(self, *args, **kwargs) -> Any:
        ...


class Command(BaseCommand):
    callback: Optional[Callable]
    params: List['Parameter']
    help: Optional[str]
    epilog: Optional[str]
    short_help: Optional[str]
    options_metavar: str
    add_help_option: bool

    def __init__(
        self,
        name: str,
        context_settings: Optional[Dict] = None,
        callback: Optional[Callable] = None,
        params: Optional[List['Parameter']] = None,
        help: Optional[str] = None,
        epilog: Optional[str] = None,
        short_help: Optional[str] = None,
        options_metavar: str = '[OPTIONS]',
        add_help_option: bool = True
    ) -> None:
        ...

    def get_params(self, ctx: Context) -> List['Parameter']:
        ...

    def format_usage(
        self,
        ctx: Context,
        formatter: HelpFormatter
    ) -> None:
        ...

    def collect_usage_pieces(self, ctx: Context) -> List[str]:
        ...

    def get_help_option_names(self, ctx: Context) -> Set[str]:
        ...

    def get_help_option(self, ctx: Context) -> Optional['Option']:
        ...

    def make_parser(self, ctx: Context) -> OptionParser:
        ...

    def format_help(self, ctx: Context, formatter: HelpFormatter) -> None:
        ...

    def format_help_text(self, ctx: Context, formatter: HelpFormatter) -> None:
        ...

    def format_options(self, ctx: Context, formatter: HelpFormatter) -> None:
        ...

    def format_epilog(self, ctx: Context, formatter: HelpFormatter) -> None:
        ...


_T = TypeVar('_T')
_Decorator = Callable[[_T], _T]


class MultiCommand(Command):
    no_args_is_help: bool
    invoke_without_command: bool
    subcommand_metavar: str
    chain: bool
    result_callback: Callable

    def __init__(
        self,
        name: Optional[str] = None,
        invoke_without_command: bool = False,
        no_args_is_help: Optional[bool] = None,
        subcommand_metavar: Optional[str] = None,
        chain: bool = False,
        result_callback: Optional[Callable] = None,
        **attrs
    ) -> None:
        ...

    def resultcallback(
        self, replace: bool = False
    ) -> _Decorator:
        ...

    def format_commands(self, ctx: Context, formatter: HelpFormatter) -> None:
        ...

    def resolve_command(
        self, ctx: Context, args: List[str]
    ) -> Tuple[str, Command, List[str]]:
        ...

    def get_command(self, ctx: Context, cmd_name: str) -> Optional[Command]:
        ...

    def list_commands(self, ctx: Context) -> Iterable[Command]:
        ...


class Group(MultiCommand):
    commands: Dict[str, Command]

    def __init__(
        self, name: Optional[str] = None, commands: Optional[Dict[str, Command]] = None, **attrs
    ) -> None:
        ...

    def add_command(self, cmd: Command, name: Optional[str] = None):
        ...

    def command(self, *args, **kwargs) -> _Decorator:
        ...

    def group(self, *args, **kwargs) -> _Decorator:
        ...


class CommandCollection(MultiCommand):
    sources: List[MultiCommand]

    def __init__(
        self, name: Optional[str] = None, sources: Optional[List[MultiCommand]] = None, **attrs
    ) -> None:
        ...

    def add_source(self, multi_cmd: MultiCommand) -> None:
        ...


class Parameter:
    param_type_name: str
    name: str
    opts: List[str]
    secondary_opts: List[str]
    type: 'ParamType'
    required: bool
    callback: Optional[Callable[[Context, 'Parameter', str], Any]]
    nargs: int
    multiple: bool
    expose_value: bool
    default: Any
    is_eager: bool
    metavar: Optional[str]
    envvar: Union[str, List[str], None]
    # properties
    human_readable_name: str

    def __init__(
        self,
        param_decls: Optional[List[str]] = None,
        type: Optional[Union[type, 'ParamType']] = None,
        required: bool = False,
        default: Optional[Any] = None,
        callback: Optional[Callable[[Context, 'Parameter', str], Any]] = None,
        nargs: Optional[int] = None,
        metavar: Optional[str] = None,
        expose_value: bool = True,
        is_eager: bool = False,
        envvar: Optional[Union[str, List[str]]] = None
    ) -> None:
        ...

    def make_metavar(self) -> str:
        ...

    def get_default(self, ctx: Context) -> Any:
        ...

    def add_to_parser(self, parser: OptionParser, ctx: Context) -> None:
        ...

    def consume_value(self, ctx: Context, opts: Dict[str, Any]) -> Any:
        ...

    def type_cast_value(self, ctx: Context, value: Any) -> Any:
        ...

    def process_value(self, ctx: Context, value: Any) -> Any:
        ...

    def value_is_missing(self, value: Any) -> bool:
        ...

    def full_process_value(self, ctx: Context, value: Any) -> Any:
        ...

    def resolve_envvar_value(self, ctx: Context) -> str:
        ...

    def value_from_envvar(self, ctx: Context) -> Union[str, List[str]]:
        ...

    def handle_parse_result(
        self, ctx: Context, opts: Dict[str, Any], args: List[str]
    ) -> Tuple[Any, List[str]]:
        ...

    def get_help_record(self, ctx: Context) -> Tuple[str, str]:
        ...

    def get_usage_pieces(self, ctx: Context) -> List[str]:
        ...


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
    help: Optional[str]
    show_default: bool

    def __init__(
        self,
        param_decls: Optional[List[str]] = None,
        show_default: bool = False,
        prompt: Union[bool, str] = False,
        confirmation_prompt: bool = False,
        hide_input: bool = False,
        is_flag: Optional[bool] = None,
        flag_value: Optional[Any] = None,
        multiple: bool = False,
        count: bool = False,
        allow_from_autoenv: bool = True,
        type: Optional[Union[type, 'ParamType']] = None,
        help: Optional[str] = None,
        **attrs
    ) -> None:
        ...

    def prompt_for_value(self, ctx: Context) -> Any:
        ...


class Argument(Parameter):
    def __init__(
        self,
        param_decls: Optional[List[str]] = None,
        required: Optional[bool] = None,
        **attrs
    ) -> None:
        ...

# cyclic dependency
from click.types import ParamType  # noqa: E402
