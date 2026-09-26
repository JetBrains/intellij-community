from _typeshed import Incomplete
from typing import ClassVar

class Token:
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, start_mark, end_mark) -> None: ...

class DirectiveToken(Token):
    id: ClassVar[str]
    name: Incomplete
    value: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, name, value, start_mark, end_mark) -> None: ...

class DocumentStartToken(Token):
    id: ClassVar[str]

class DocumentEndToken(Token):
    id: ClassVar[str]

class StreamStartToken(Token):
    id: ClassVar[str]
    start_mark: Incomplete
    end_mark: Incomplete
    encoding: Incomplete
    def __init__(self, start_mark=None, end_mark=None, encoding=None) -> None: ...

class StreamEndToken(Token):
    id: ClassVar[str]

class BlockSequenceStartToken(Token):
    id: ClassVar[str]

class BlockMappingStartToken(Token):
    id: ClassVar[str]

class BlockEndToken(Token):
    id: ClassVar[str]

class FlowSequenceStartToken(Token):
    id: ClassVar[str]

class FlowMappingStartToken(Token):
    id: ClassVar[str]

class FlowSequenceEndToken(Token):
    id: ClassVar[str]

class FlowMappingEndToken(Token):
    id: ClassVar[str]

class KeyToken(Token):
    id: ClassVar[str]

class ValueToken(Token):
    id: ClassVar[str]

class BlockEntryToken(Token):
    id: ClassVar[str]

class FlowEntryToken(Token):
    id: ClassVar[str]

class AliasToken(Token):
    id: ClassVar[str]
    value: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, value, start_mark, end_mark) -> None: ...

class AnchorToken(Token):
    id: ClassVar[str]
    value: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, value, start_mark, end_mark) -> None: ...

class TagToken(Token):
    id: ClassVar[str]
    value: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    def __init__(self, value, start_mark, end_mark) -> None: ...

class ScalarToken(Token):
    id: ClassVar[str]
    value: Incomplete
    plain: Incomplete
    start_mark: Incomplete
    end_mark: Incomplete
    style: Incomplete
    def __init__(self, value, plain, start_mark, end_mark, style=None) -> None: ...
