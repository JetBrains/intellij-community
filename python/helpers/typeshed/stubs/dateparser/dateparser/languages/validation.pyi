from _typeshed import Incomplete
from logging import Logger

class LanguageValidator:
    logger: Logger | None
    VALID_KEYS: list[str]
    @classmethod
    def get_logger(cls) -> Logger: ...
    @classmethod
    def validate_info(cls, language_id, info: dict[str, Incomplete]) -> bool: ...
