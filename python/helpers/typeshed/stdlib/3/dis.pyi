from typing import List, Union, Iterator, Tuple, Optional, Any, IO, NamedTuple

from opcode import (hasconst, hasname, hasjrel, hasjabs, haslocal, hascompare,
                    hasfree, hasnargs, cmp_op, opname , opmap , HAVE_ARGUMENT,
                    EXTENDED_ARG, stack_effect)

import types

_have_code = Union[types.MethodType, types.FunctionType, types.CodeType, type]
_have_code_or_string = Union[_have_code, str, bytes]


Instruction = NamedTuple("Instruction", [
        ('opname', str),
        ('opcode', int),
        ('arg', Optional[int]),
        ('argval', Any),
        ('argrepr', str),
        ('offset', int),
        ('starts_line', Optional[int]),
        ('is_jump_target', bool)
    ])


# if sys.version_info >= (3, 4): 
class Bytecode:
    codeobj = ...  # type: types.CodeType
    first_line = ...  # type: int
    def __init__(self, x: _have_code_or_string, *, first_line: int=...,
                 current_offset: int=...) -> None: ...
    def __iter__(self) -> Iterator[Instruction]: ...
    def __repr__(self) -> str: ...
    def info(self) -> str: ...
    def dis(self) -> str: ...

    @classmethod
    def from_traceback(cls, tb: types.TracebackType) -> Bytecode: ...

 
COMPILER_FLAG_NAMES = ...  # type:  Dict[int, str]


def pretty_flags(flags: int) -> str: ...
def findlabels(code: _have_code) -> List[int]: ...
def findlinestarts(code: _have_code) -> Iterator[Tuple[int, int]]: ...

# Signature changes are not allowed by mypy
# 'All conditional function variants must have identical signatures'
# TODO: mypy issue #698

# if sys.version_info >= (3, 2):
def code_info(x: _have_code_or_string) -> str: ...
    
# `file` parameter requires sys.version_info >= (3, 4):
def dis(x: _have_code_or_string = ..., *, file = ...) -> None: ...
def distb(tb: types.TracebackType = ..., *, file: IO[str] = ...) -> None: ...
def disassemble(co: _have_code, lasti: int = ..., *, file = ...) -> None: ...
def show_code(co: _have_code, *, file: IO[str]=...) -> None: ...

def get_instructions(x: _have_code, *, first_line: int = ...) -> Iterator[Instruction]: ...
