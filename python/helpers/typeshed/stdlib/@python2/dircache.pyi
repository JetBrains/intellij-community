#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import MutableSequence, Text

def reset() -> None: ...
def listdir(path: Text) -> list[str]: ...

opendir = listdir

def annotate(head: Text, list: MutableSequence[str] | MutableSequence[Text] | MutableSequence[str | Text]) -> None: ...
