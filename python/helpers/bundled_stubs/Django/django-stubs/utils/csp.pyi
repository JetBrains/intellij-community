import sys
from collections.abc import Collection, Mapping

from django.utils.functional import SimpleLazyObject
from typing_extensions import override

if sys.version_info >= (3, 11):
    from enum import StrEnum as _StrEnum
else:
    from enum import Enum

    class _ReprEnum(Enum): ...  # type: ignore[misc]
    class _StrEnum(str, _ReprEnum): ...  # type: ignore[misc]

class CSP(_StrEnum):
    HEADER_ENFORCE = "Content-Security-Policy"
    HEADER_REPORT_ONLY = "Content-Security-Policy-Report-Only"

    NONE = "'none'"
    REPORT_SAMPLE = "'report-sample'"
    SELF = "'self'"
    STRICT_DYNAMIC = "'strict-dynamic'"
    UNSAFE_EVAL = "'unsafe-eval'"
    UNSAFE_HASHES = "'unsafe-hashes'"
    UNSAFE_INLINE = "'unsafe-inline'"
    WASM_UNSAFE_EVAL = "'wasm-unsafe-eval'"

    NONCE = "<CSP_NONCE_SENTINEL>"

class LazyNonce(SimpleLazyObject):
    def __init__(self) -> None: ...
    @override
    def __bool__(self) -> bool: ...

def generate_nonce() -> str: ...
def build_policy(config: Mapping[str, Collection[str] | str], nonce: SimpleLazyObject | str | None = None) -> str: ...
