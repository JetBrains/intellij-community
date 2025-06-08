from typing import Final, Literal

GET_ITERATOR_CHUNK_SIZE: Final[int]

MULTI: Literal["multi"]
SINGLE: Literal["single"]
NO_RESULTS: Literal["no results"]
CURSOR: Literal["cursor"]
ROW_COUNT: Literal["row count"]

ORDER_DIR: dict[str, tuple[str, str]]

INNER: Literal["INNER JOIN"]
LOUTER: Literal["LEFT OUTER JOIN"]
