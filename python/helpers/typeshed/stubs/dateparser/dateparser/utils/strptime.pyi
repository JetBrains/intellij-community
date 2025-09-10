import re
from datetime import datetime
from typing import Any, Final

TIME_MATCHER: Final[re.Pattern[str]]
MS_SEARCHER: Final[re.Pattern[str]]

def patch_strptime() -> Any: ...
def strptime(date_string, format) -> datetime: ...
