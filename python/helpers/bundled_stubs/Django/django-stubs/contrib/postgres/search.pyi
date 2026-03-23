import re
from typing import Any, ClassVar, TypeAlias

from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Expression, Field, FloatField, TextField
from django.db.models.expressions import Combinable, CombinedExpression, Func, Value
from django.db.models.lookups import Lookup
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from psycopg.adapt import Dumper
from typing_extensions import Self, override

from .utils import CheckPostgresInstalledMixin

_Expression: TypeAlias = str | Combinable | SearchQueryCombinable

class UTF8Dumper(Dumper):
    @override
    def dump(self, obj: object) -> bytes: ...

def quote_lexeme(value: str) -> str: ...

spec_chars_re: re.Pattern[str]
multiple_spaces_re: re.Pattern[str]

def normalize_spaces(val: str) -> str | None: ...
def psql_escape(query: str) -> str | None: ...

class SearchVectorExact(Lookup):
    @override
    def process_rhs(self, qn: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...
    @override
    def as_sql(self, qn: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...

class SearchVectorField(CheckPostgresInstalledMixin, Field): ...
class SearchQueryField(CheckPostgresInstalledMixin, Field): ...

class SearchConfig(Expression):
    config: _Expression | None
    def __init__(self, config: _Expression) -> None: ...
    @classmethod
    def from_parameter(cls, config: _Expression | None) -> SearchConfig: ...

class SearchVectorCombinable:
    ADD: str

class SearchVector(SearchVectorCombinable, Func):
    config: _Expression | None
    function: str
    arg_joiner: str
    output_field: ClassVar[SearchVectorField]
    def __init__(
        self, *expressions: _Expression, config: _Expression | None = None, weight: Any | None = None
    ) -> None: ...
    @override
    def as_sql(  # type: ignore[override]
        self,
        compiler: SQLCompiler,
        connection: BaseDatabaseWrapper,
        function: str | None = None,
        template: str | None = None,
    ) -> _AsSqlType: ...

class CombinedSearchVector(SearchVectorCombinable, CombinedExpression):
    def __init__(
        self,
        lhs: Combinable,
        connector: str,
        rhs: Combinable,
        config: _Expression | None,
        output_field: Field | None = None,
    ) -> None: ...

class SearchQueryCombinable:
    BITAND: str
    BITOR: str
    def __or__(self, other: SearchQueryCombinable) -> Self: ...
    def __ror__(self, other: SearchQueryCombinable) -> Self: ...
    def __and__(self, other: SearchQueryCombinable) -> Self: ...
    def __rand__(self, other: SearchQueryCombinable) -> Self: ...

class SearchQuery(SearchQueryCombinable, Func):  # type: ignore[misc]
    output_field: ClassVar[SearchQueryField]
    SEARCH_TYPES: dict[str, str]
    def __init__(
        self,
        value: _Expression,
        output_field: Field | None = None,
        *,
        config: _Expression | None = None,
        invert: bool = False,
        search_type: str = "plain",
    ) -> None: ...
    @override
    def as_sql(  # type: ignore[override]
        self,
        compiler: SQLCompiler,
        connection: BaseDatabaseWrapper,
        function: str | None = None,
        template: str | None = None,
    ) -> _AsSqlType: ...
    @override
    def __invert__(self) -> Self: ...  # type: ignore[override]

class CombinedSearchQuery(SearchQueryCombinable, CombinedExpression):  # type: ignore[misc]
    def __init__(
        self,
        lhs: Combinable,
        connector: str,
        rhs: Combinable,
        config: _Expression | None,
        output_field: Field | None = None,
    ) -> None: ...

class SearchRank(Func):
    output_field: ClassVar[FloatField]
    def __init__(
        self,
        vector: SearchVector | _Expression,
        query: SearchQuery | _Expression,
        weights: Any | None = None,
        normalization: Any | None = None,
        cover_density: bool = False,
    ) -> None: ...

class SearchHeadline(Func):
    function: str
    template: str
    output_field: ClassVar[TextField]
    def __init__(
        self,
        expression: _Expression,
        query: _Expression,
        *,
        config: _Expression | None = None,
        start_sel: Any | None = None,
        stop_sel: Any | None = None,
        max_words: int | None = None,
        min_words: int | None = None,
        short_word: str | None = None,
        highlight_all: bool | None = None,
        max_fragments: int | None = None,
        fragment_delimiter: str | None = None,
    ) -> None: ...
    @override
    def as_sql(  # type: ignore[override]
        self,
        compiler: SQLCompiler,
        connection: BaseDatabaseWrapper,
        function: str | None = None,
        template: str | None = None,
    ) -> _AsSqlType: ...

class TrigramBase(Func):
    output_field: ClassVar[FloatField]
    def __init__(self, expression: _Expression, string: str, **extra: Any) -> None: ...

class TrigramWordBase(Func):
    output_field: ClassVar[FloatField]
    def __init__(self, string: str, expression: _Expression, **extra: Any) -> None: ...

class TrigramSimilarity(TrigramBase): ...
class TrigramDistance(TrigramBase): ...
class TrigramWordDistance(TrigramWordBase): ...
class TrigramStrictWordDistance(TrigramWordBase): ...
class TrigramWordSimilarity(TrigramWordBase): ...
class TrigramStrictWordSimilarity(TrigramWordBase): ...

class LexemeCombinable:
    BITAND: str
    BITOR: str
    def __or__(self, other: LexemeCombinable) -> CombinedLexeme: ...
    def __ror__(self, other: LexemeCombinable) -> CombinedLexeme: ...
    def __and__(self, other: LexemeCombinable) -> CombinedLexeme: ...
    def __rand__(self, other: LexemeCombinable) -> CombinedLexeme: ...

class Lexeme(LexemeCombinable, Value):  # type: ignore[misc]
    def __init__(
        self,
        value: Any,
        output_field: Any | None = None,
        *,
        invert: bool = False,
        prefix: bool = False,
        weight: Any | None = None,
    ) -> None: ...
    @override
    def __invert__(self) -> Self: ...  # type: ignore[override]

class CombinedLexeme(LexemeCombinable, CombinedExpression):  # type: ignore[misc]
    @override
    def __invert__(self) -> Self: ...  # type: ignore[override]
