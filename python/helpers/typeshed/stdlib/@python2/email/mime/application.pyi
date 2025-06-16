#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from email.mime.nonmultipart import MIMENonMultipart
from typing import Callable, Union

_ParamsType = Union[str, None, tuple[str, str | None, str]]

class MIMEApplication(MIMENonMultipart):
    def __init__(
        self, _data: bytes, _subtype: str = ..., _encoder: Callable[[MIMEApplication], None] = ..., **_params: _ParamsType
    ) -> None: ...
