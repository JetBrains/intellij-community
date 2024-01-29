#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import dis

from _pydevd_bundle.smart_step_into.pydevd_smart_step_into_util import Instruction

_BINARY_OPS = set([opname for opname in dis.opname if opname.startswith('BINARY_')])

_BINARY_OP_MAP = {
    '**': '__pow__',
    '*': '__mul__',
    '@': '__matmul__',
    '//': '__floordiv__',
    '/': '__div__',
    '%': '__mod__',
    '+': '__add__',
    '-': '__sub__',
    '<<': '__lshift__',
    '>>': '__rshift__',
    '&': '__and__',
    '|': '__or__',
    '^': '__xor__',
    'BINARY_SUBSCR': '__getitem__',
    'BINARY_SLICE': '__getitem__',
}


def _is_binary_opname(opname):
    return opname in _BINARY_OPS


def get_stepping_variants(code):
    stk = []
    for instruction in dis.get_instructions(code):
        if instruction.opname == 'CALL':
            while stk and stk[-1].opname not in ('LOAD_NAME', 'LOAD_GLOBAL',
                                                 'LOAD_ATTR', 'LOAD_METHOD',
                                                 'LOAD_DEREF'):
                stk.pop()
            if not stk:
                continue
            tos = stk.pop()
            yield Instruction(
                tos.opname,
                tos.opcode,
                tos.arg,
                tos.argval,
                instruction.offset,
                tos.positions.lineno
            )
        elif _is_binary_opname(instruction.opname):
            yield Instruction(
                instruction.opname,
                instruction.opcode,
                instruction.arg,
                _BINARY_OP_MAP[
                    instruction.argrepr if instruction.argrepr else instruction.opname],
                instruction.offset,
                instruction.positions.lineno
            )
        else:
            stk.append(instruction)
