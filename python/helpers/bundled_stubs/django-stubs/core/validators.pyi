from collections.abc import Callable, Collection, Sequence, Sized
from decimal import Decimal
from re import Pattern, RegexFlag
from typing import Any

from django.core.files.base import File
from django.utils.deconstruct import _Deconstructible
from django.utils.functional import _StrOrPromise
from typing_extensions import TypeAlias

EMPTY_VALUES: Any

_Regex: TypeAlias = str | Pattern[str]

_ValidatorCallable: TypeAlias = Callable[[Any], None]  # noqa: PYI047

class RegexValidator(_Deconstructible):
    regex: _Regex  # Pattern[str] on instance, but may be str on class definition
    message: _StrOrPromise
    code: str
    inverse_match: bool
    flags: int
    def __init__(
        self,
        regex: _Regex | None = ...,
        message: _StrOrPromise | None = ...,
        code: str | None = ...,
        inverse_match: bool | None = ...,
        flags: RegexFlag | None = ...,
    ) -> None: ...
    def __call__(self, value: Any) -> None: ...

class URLValidator(RegexValidator):
    ul: str
    ipv4_re: str
    ipv6_re: str
    hostname_re: str
    domain_re: str
    tld_re: str
    host_re: str
    schemes: Sequence[str]
    unsafe_chars: frozenset[str]
    max_length: int
    def __init__(self, schemes: Sequence[str] | None = ..., **kwargs: Any) -> None: ...
    def __call__(self, value: Any) -> None: ...

integer_validator: RegexValidator

def validate_integer(value: float | str | None) -> None: ...

class EmailValidator(_Deconstructible):
    message: _StrOrPromise
    code: str
    user_regex: Pattern[str]
    domain_regex: Pattern[str]
    literal_regex: Pattern[str]
    domain_allowlist: Sequence[str]
    def __init__(
        self,
        message: _StrOrPromise | None = ...,
        code: str | None = ...,
        allowlist: Sequence[str] | None = ...,
    ) -> None: ...
    def __call__(self, value: str | None) -> None: ...
    def validate_domain_part(self, domain_part: str) -> bool: ...
    def __eq__(self, other: object) -> bool: ...

validate_email: EmailValidator
slug_re: Pattern[str]
validate_slug: RegexValidator
slug_unicode_re: Pattern[str]
validate_unicode_slug: RegexValidator

def validate_ipv4_address(value: str) -> None: ...
def validate_ipv6_address(value: str) -> None: ...
def validate_ipv46_address(value: str) -> None: ...

_IPValidator: TypeAlias = tuple[list[Callable[[Any], None]], str]
ip_address_validator_map: dict[str, _IPValidator]

def ip_address_validators(protocol: str, unpack_ipv4: bool) -> _IPValidator: ...
def int_list_validator(
    sep: str = ..., message: _StrOrPromise | None = ..., code: str = ..., allow_negative: bool = ...
) -> RegexValidator: ...

validate_comma_separated_integer_list: RegexValidator

class BaseValidator(_Deconstructible):
    message: _StrOrPromise
    code: str
    limit_value: Any
    def __init__(self, limit_value: Any | Callable[[], Any], message: _StrOrPromise | None = ...) -> None: ...
    def __call__(self, value: Any) -> None: ...
    def compare(self, a: Any, b: Any) -> bool: ...
    def clean(self, x: Any) -> Any: ...
    def __eq__(self, other: object) -> bool: ...

class MaxValueValidator(BaseValidator): ...
class MinValueValidator(BaseValidator): ...

class StepValueValidator(BaseValidator):
    offset: int | None
    def __init__(
        self, limit_value: Any | Callable[[], Any], message: _StrOrPromise | None = ..., offset: int | None = ...
    ) -> None: ...

class MinLengthValidator(BaseValidator):
    def clean(self, x: Sized) -> int: ...

class MaxLengthValidator(BaseValidator):
    def clean(self, x: Sized) -> int: ...

class DecimalValidator(_Deconstructible):
    messages: dict[str, str]
    max_digits: int | None
    decimal_places: int | None
    def __init__(self, max_digits: int | None, decimal_places: int | None) -> None: ...
    def __call__(self, value: Decimal) -> None: ...
    def __eq__(self, other: object) -> bool: ...

class FileExtensionValidator(_Deconstructible):
    message: _StrOrPromise
    code: str
    allowed_extensions: Collection[str] | None
    def __init__(
        self,
        allowed_extensions: Collection[str] | None = ...,
        message: _StrOrPromise | None = ...,
        code: str | None = ...,
    ) -> None: ...
    def __call__(self, value: File) -> None: ...

def get_available_image_extensions() -> Sequence[str]: ...
def validate_image_file_extension(value: File) -> None: ...

class ProhibitNullCharactersValidator(_Deconstructible):
    message: _StrOrPromise
    code: str
    def __init__(self, message: _StrOrPromise | None = ..., code: str | None = ...) -> None: ...
    def __call__(self, value: Any) -> None: ...
