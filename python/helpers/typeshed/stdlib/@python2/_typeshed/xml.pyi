#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# Stub-only types. This module does not exist at runtime.

from typing import Any, Protocol

# As defined https://docs.python.org/3/library/xml.dom.html#domimplementation-objects
class DOMImplementation(Protocol):
    def hasFeature(self, feature: str, version: str | None) -> bool: ...
    def createDocument(self, namespaceUri: str, qualifiedName: str, doctype: Any | None) -> Any: ...
    def createDocumentType(self, qualifiedName: str, publicId: str, systemId: str) -> Any: ...
