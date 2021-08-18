from typing import Any

argument_types: Any
converters: Any

def run(namespace: Any | None = ..., action_prefix: str = ..., args: Any | None = ...): ...
def fail(message, code: int = ...): ...
def find_actions(namespace, action_prefix): ...
def print_usage(actions): ...
def analyse_action(func): ...
def make_shell(init_func: Any | None = ..., banner: Any | None = ..., use_ipython: bool = ...): ...
def make_runserver(
    app_factory,
    hostname: str = ...,
    port: int = ...,
    use_reloader: bool = ...,
    use_debugger: bool = ...,
    use_evalex: bool = ...,
    threaded: bool = ...,
    processes: int = ...,
    static_files: Any | None = ...,
    extra_files: Any | None = ...,
    ssl_context: Any | None = ...,
): ...
