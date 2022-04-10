from typing import Any

import passlib.utils.handlers as uh

class mysql323(uh.StaticHandler):
    name: str
    checksum_size: int
    checksum_chars: Any

class mysql41(uh.StaticHandler):
    name: str
    checksum_chars: Any
    checksum_size: int

# Names in __all__ with no definition:
#   mysq41
