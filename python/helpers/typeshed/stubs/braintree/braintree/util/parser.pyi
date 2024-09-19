from xml.dom.minidom import Document

binary_type = bytes

class Parser:
    doc: Document
    def __init__(self, xml) -> None: ...
    def parse(self): ...
