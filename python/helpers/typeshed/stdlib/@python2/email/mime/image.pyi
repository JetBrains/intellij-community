#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from email.mime.nonmultipart import MIMENonMultipart

class MIMEImage(MIMENonMultipart):
    def __init__(self, _imagedata, _subtype=..., _encoder=..., **_params) -> None: ...
