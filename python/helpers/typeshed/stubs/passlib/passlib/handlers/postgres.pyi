from typing import Any

import passlib.utils.handlers as uh

class postgres_md5(uh.HasUserContext, uh.StaticHandler):  # type: ignore
    name: str
    checksum_chars: Any
    checksum_size: int
