from _typeshed import Incomplete
from typing import Final, final

import gdb

class Disassembler:
    def __init__(self, name: str) -> None: ...
    def __call__(self, info): ...

class DisassembleInfo:
    address: Incomplete
    architecture: Incomplete
    progspace: Incomplete
    def __init__(self, /, *args, **kwargs) -> None: ...
    def address_part(self, address) -> DisassemblerAddressPart: ...
    def is_valid(self) -> bool: ...
    def read_memory(self, len, offset: int = 0): ...
    def text_part(self, string, style) -> DisassemblerTextPart: ...

class DisassemblerPart:
    def __init__(self, /, *args, **kwargs) -> None: ...

@final
class DisassemblerAddressPart(DisassemblerPart):
    address: Incomplete
    string: str

@final
class DisassemblerTextPart(DisassemblerPart):
    string: str
    style: Incomplete

@final
class DisassemblerResult:
    def __init__(self, /, *args, **kwargs) -> None: ...
    length: Incomplete
    parts: Incomplete
    string: str

STYLE_TEXT: Final = 0
STYLE_MNEMONIC: Final = 1
STYLE_SUB_MNEMONIC: Final = 2
STYLE_ASSEMBLER_DIRECTIVE: Final = 3
STYLE_REGISTER: Final = 4
STYLE_IMMEDIATE: Final = 5
STYLE_ADDRESS: Final = 6
STYLE_ADDRESS_OFFSET: Final = 7
STYLE_SYMBOL: Final = 8
STYLE_COMMENT_START: Final = 9

def builtin_disassemble(INFO: DisassembleInfo, MEMORY_SOURCE=None) -> None: ...

class maint_info_py_disassemblers_cmd(gdb.Command):
    def __init__(self) -> None: ...
    def invoke(self, args, from_tty): ...

def register_disassembler(disassembler: type[Disassembler], architecture: str | None = None): ...
