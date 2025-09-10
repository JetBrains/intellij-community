import random
from abc import abstractmethod
from collections.abc import Callable, Iterator, MutableMapping, Sequence
from typing import Any, Final, Literal, overload
from typing_extensions import Self, TypeAlias

class SequenceGenerator:
    length: int | None
    requested_entropy: str
    rng: random.Random
    @property
    @abstractmethod
    def symbol_count(self) -> int: ...
    def __init__(self, entropy: int | None = None, length: int | None = None, rng: random.Random | None = None) -> None: ...
    @property
    def entropy_per_symbol(self) -> float: ...
    @property
    def entropy(self) -> float: ...
    def __next__(self) -> str: ...
    @overload
    def __call__(self, returns: None = None) -> str: ...
    @overload
    def __call__(self, returns: int) -> list[str]: ...
    @overload
    def __call__(self, returns: Callable[[Any], Iterator[Any]]) -> Iterator[str]: ...  # "returns" must be the "iter" builtin
    def __iter__(self) -> Self: ...

_Charset: TypeAlias = Literal["ascii_72", "ascii_62", "ascii_50", "hex"]
default_charsets: Final[dict[_Charset, str]]

class WordGenerator(SequenceGenerator):
    charset: _Charset
    chars: str | bytes | None
    def __init__(
        self,
        chars: str | bytes | None = None,
        charset: _Charset | None = None,
        *,
        entropy: int | None = None,
        length: int | None = None,
        rng: random.Random | None = None,
    ) -> None: ...
    @property
    def symbol_count(self) -> int: ...

@overload
def genword(
    entropy: int | None = None,
    length: int | None = None,
    returns: None = None,
    *,
    chars: str | None = None,
    charset: _Charset | None = None,
    rng: random.Random | None = None,
) -> str: ...
@overload
def genword(
    returns: int,
    entropy: int | None = None,
    length: int | None = None,
    *,
    chars: str | None = None,
    charset: _Charset | None = None,
    rng: random.Random | None = None,
) -> list[str]: ...
@overload
def genword(
    returns: Callable[[Any], Iterator[Any]],
    entropy: int | None = None,
    length: int | None = None,
    *,
    chars: str | None = None,
    charset: _Charset | None = None,
    rng: random.Random | None = None,
) -> Iterator[str]: ...

class WordsetDict(MutableMapping[Any, Any]):
    paths: dict[str, str] | None
    def __init__(self, *args, **kwds) -> None: ...
    def __getitem__(self, key: str) -> tuple[str | bytes, ...]: ...
    def set_path(self, key: str, path: str) -> None: ...
    def __setitem__(self, key: str, value: tuple[str | bytes, ...]) -> None: ...
    def __delitem__(self, key: str) -> None: ...
    def __iter__(self) -> Iterator[str]: ...
    def __len__(self) -> int: ...
    def __contains__(self, key: object) -> bool: ...

default_wordsets: WordsetDict
_Wordset: TypeAlias = Literal["eff_long", "eff_short", "eff_prefixed", "bip39"]

class PhraseGenerator(SequenceGenerator):
    wordset: _Wordset
    words: Sequence[str | bytes] | None
    sep: str | None
    def __init__(
        self,
        wordset: _Wordset | None = None,
        words: Sequence[str | bytes] | None = None,
        sep: str | bytes | None = None,
        *,
        entropy: int | None = None,
        length: int | None = None,
        rng: random.Random | None = None,
    ) -> None: ...
    @property
    def symbol_count(self) -> int: ...

@overload
def genphrase(
    entropy: int | None = None,
    length: int | None = None,
    returns: None = None,
    *,
    wordset: _Wordset | None = None,
    words: Sequence[str | bytes] | None = None,
    sep: str | bytes | None = None,
    rng: random.Random | None = None,
) -> str: ...
@overload
def genphrase(
    returns: int,
    entropy: int | None = None,
    length: int | None = None,
    *,
    wordset: _Wordset | None = None,
    words: Sequence[str | bytes] | None = None,
    sep: str | bytes | None = None,
    rng: random.Random | None = None,
) -> list[str]: ...
@overload
def genphrase(
    returns: Callable[[Any], Iterator[Any]],
    entropy: int | None = None,
    length: int | None = None,
    *,
    wordset: _Wordset | None = None,
    words: Sequence[str | bytes] | None = None,
    sep: str | bytes | None = None,
    rng: random.Random | None = None,
) -> Iterator[str]: ...

__all__ = ["genword", "default_charsets", "genphrase", "default_wordsets"]
