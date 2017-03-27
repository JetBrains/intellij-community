# Hand-written stub, incomplete

from typing import Union

from lxml.etree import ElementBase, XMLParser

class ObjectifiedElement(ElementBase):
    pass

def fromstring(text: Union[bytes, str],
               parser: XMLParser = ...,
               *,
               base_url: Union[bytes, str] = ...) -> ObjectifiedElement: ...
