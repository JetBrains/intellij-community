import re
from typing import Final

VALID_PATH_SEGMENT_REGEX: Final[re.Pattern[str]]

def is_invalid_path_segment(value: object) -> bool: ...
