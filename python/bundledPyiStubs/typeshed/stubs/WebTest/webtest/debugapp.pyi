from _typeshed import StrOrBytesPath
from _typeshed.wsgi import StartResponse, WSGIEnvironment
from collections.abc import Iterable
from typing import TypedDict, type_check_only
from typing_extensions import Unpack

@type_check_only
class _DebugAppParams(TypedDict, total=False):
    form: StrOrBytesPath | bytes | None
    show_form: bool

__all__ = ["DebugApp", "make_debug_app"]

class DebugApp:
    form: bytes | None
    show_form: bool
    def __init__(self, form: StrOrBytesPath | bytes | None = None, show_form: bool = False) -> None: ...
    def __call__(self, environ: WSGIEnvironment, start_response: StartResponse) -> Iterable[bytes]: ...

debug_app: DebugApp

def make_debug_app(global_conf: object, **local_conf: Unpack[_DebugAppParams]) -> DebugApp: ...
