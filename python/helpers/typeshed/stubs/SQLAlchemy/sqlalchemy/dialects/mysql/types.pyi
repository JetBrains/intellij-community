from typing import Any

import sqlalchemy.types as sqltypes

class _NumericType:
    unsigned: Any
    zerofill: Any
    def __init__(self, unsigned: bool = ..., zerofill: bool = ..., **kw) -> None: ...

class _FloatType(_NumericType, sqltypes.Float):
    scale: Any
    def __init__(self, precision: Any | None = ..., scale: Any | None = ..., asdecimal: bool = ..., **kw) -> None: ...

class _IntegerType(_NumericType, sqltypes.Integer):
    display_width: Any
    def __init__(self, display_width: Any | None = ..., **kw) -> None: ...

class _StringType(sqltypes.String):
    charset: Any
    ascii: Any
    unicode: Any
    binary: Any
    national: Any
    def __init__(
        self,
        charset: Any | None = ...,
        collation: Any | None = ...,
        ascii: bool = ...,
        binary: bool = ...,
        unicode: bool = ...,
        national: bool = ...,
        **kw,
    ) -> None: ...

class _MatchType(sqltypes.Float, sqltypes.MatchType):  # type: ignore[misc]  # incompatible with base class
    def __init__(self, **kw) -> None: ...

class NUMERIC(_NumericType, sqltypes.NUMERIC):
    __visit_name__: str
    def __init__(self, precision: Any | None = ..., scale: Any | None = ..., asdecimal: bool = ..., **kw) -> None: ...

class DECIMAL(_NumericType, sqltypes.DECIMAL):
    __visit_name__: str
    def __init__(self, precision: Any | None = ..., scale: Any | None = ..., asdecimal: bool = ..., **kw) -> None: ...

class DOUBLE(_FloatType):
    __visit_name__: str
    def __init__(self, precision: Any | None = ..., scale: Any | None = ..., asdecimal: bool = ..., **kw) -> None: ...

class REAL(_FloatType, sqltypes.REAL):
    __visit_name__: str
    def __init__(self, precision: Any | None = ..., scale: Any | None = ..., asdecimal: bool = ..., **kw) -> None: ...

class FLOAT(_FloatType, sqltypes.FLOAT):
    __visit_name__: str
    def __init__(self, precision: Any | None = ..., scale: Any | None = ..., asdecimal: bool = ..., **kw) -> None: ...
    def bind_processor(self, dialect) -> None: ...

class INTEGER(_IntegerType, sqltypes.INTEGER):
    __visit_name__: str
    def __init__(self, display_width: Any | None = ..., **kw) -> None: ...

class BIGINT(_IntegerType, sqltypes.BIGINT):
    __visit_name__: str
    def __init__(self, display_width: Any | None = ..., **kw) -> None: ...

class MEDIUMINT(_IntegerType):
    __visit_name__: str
    def __init__(self, display_width: Any | None = ..., **kw) -> None: ...

class TINYINT(_IntegerType):
    __visit_name__: str
    def __init__(self, display_width: Any | None = ..., **kw) -> None: ...

class SMALLINT(_IntegerType, sqltypes.SMALLINT):
    __visit_name__: str
    def __init__(self, display_width: Any | None = ..., **kw) -> None: ...

class BIT(sqltypes.TypeEngine):
    __visit_name__: str
    length: Any
    def __init__(self, length: Any | None = ...) -> None: ...
    def result_processor(self, dialect, coltype): ...

class TIME(sqltypes.TIME):
    __visit_name__: str
    fsp: Any
    def __init__(self, timezone: bool = ..., fsp: Any | None = ...) -> None: ...
    def result_processor(self, dialect, coltype): ...

class TIMESTAMP(sqltypes.TIMESTAMP):
    __visit_name__: str
    fsp: Any
    def __init__(self, timezone: bool = ..., fsp: Any | None = ...) -> None: ...

class DATETIME(sqltypes.DATETIME):
    __visit_name__: str
    fsp: Any
    def __init__(self, timezone: bool = ..., fsp: Any | None = ...) -> None: ...

class YEAR(sqltypes.TypeEngine):
    __visit_name__: str
    display_width: Any
    def __init__(self, display_width: Any | None = ...) -> None: ...

class TEXT(_StringType, sqltypes.TEXT):
    __visit_name__: str
    def __init__(self, length: Any | None = ..., **kw) -> None: ...

class TINYTEXT(_StringType):
    __visit_name__: str
    def __init__(self, **kwargs) -> None: ...

class MEDIUMTEXT(_StringType):
    __visit_name__: str
    def __init__(self, **kwargs) -> None: ...

class LONGTEXT(_StringType):
    __visit_name__: str
    def __init__(self, **kwargs) -> None: ...

class VARCHAR(_StringType, sqltypes.VARCHAR):
    __visit_name__: str
    def __init__(self, length: Any | None = ..., **kwargs) -> None: ...

class CHAR(_StringType, sqltypes.CHAR):
    __visit_name__: str
    def __init__(self, length: Any | None = ..., **kwargs) -> None: ...

class NVARCHAR(_StringType, sqltypes.NVARCHAR):
    __visit_name__: str
    def __init__(self, length: Any | None = ..., **kwargs) -> None: ...

class NCHAR(_StringType, sqltypes.NCHAR):
    __visit_name__: str
    def __init__(self, length: Any | None = ..., **kwargs) -> None: ...

class TINYBLOB(sqltypes._Binary):
    __visit_name__: str

class MEDIUMBLOB(sqltypes._Binary):
    __visit_name__: str

class LONGBLOB(sqltypes._Binary):
    __visit_name__: str
