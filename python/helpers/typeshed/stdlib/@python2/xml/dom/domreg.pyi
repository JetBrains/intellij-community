#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from _typeshed.xml import DOMImplementation
from typing import Callable, Iterable

well_known_implementations: dict[str, str]
registered: dict[str, Callable[[], DOMImplementation]]

def registerDOMImplementation(name: str, factory: Callable[[], DOMImplementation]) -> None: ...
def getDOMImplementation(name: str | None = ..., features: str | Iterable[tuple[str, str | None]] = ...) -> DOMImplementation: ...
