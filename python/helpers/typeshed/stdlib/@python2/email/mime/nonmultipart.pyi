#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from email.mime.base import MIMEBase

class MIMENonMultipart(MIMEBase):
    def attach(self, payload): ...
