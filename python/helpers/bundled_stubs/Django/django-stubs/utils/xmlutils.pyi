from xml.sax.saxutils import XMLGenerator
from xml.sax.xmlreader import AttributesImpl

from typing_extensions import override

class UnserializableContentError(ValueError):
    def __init__(self, msg: str = "Control characters are not supported in XML 1.0"): ...

class SimplerXMLGenerator(XMLGenerator):
    def addQuickElement(self, name: str, contents: str | None = None, attrs: dict[str, str] | None = None) -> None: ...
    @override
    def characters(self, content: str) -> None: ...
    @override
    def startElement(self, name: str, attrs: AttributesImpl) -> None: ...
