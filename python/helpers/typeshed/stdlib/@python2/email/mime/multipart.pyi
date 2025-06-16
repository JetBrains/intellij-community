#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from email.mime.base import MIMEBase

class MIMEMultipart(MIMEBase):
    def __init__(self, _subtype=..., boundary=..., _subparts=..., **_params) -> None: ...
