from click.core import Optional, Context


def get_current_context(silent: bool = False) -> Context:
    ...


def push_context(ctx: Context) -> None:
    ...


def pop_context() -> None:
    ...


def resolve_color_default(color: bool = None) -> Optional[bool]:
    ...
