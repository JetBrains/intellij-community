#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import sys

if sys.platform == "win32":

    _SequenceType = list[tuple[str, str | None, int]]

    AdminExecuteSequence: _SequenceType
    AdminUISequence: _SequenceType
    AdvtExecuteSequence: _SequenceType
    InstallExecuteSequence: _SequenceType
    InstallUISequence: _SequenceType

    tables: list[str]
