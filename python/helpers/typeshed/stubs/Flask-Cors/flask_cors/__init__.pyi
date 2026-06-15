from logging import Logger
from typing import Final

from .decorator import cross_origin as cross_origin
from .extension import CORS as CORS

__version__: Final[str]

rootlogger: Logger

__all__ = ["CORS", "__version__", "cross_origin"]
