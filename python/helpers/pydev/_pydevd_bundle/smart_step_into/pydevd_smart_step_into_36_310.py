#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import dis

from _pydevd_bundle.smart_step_into.pydevd_smart_step_into_util import Instruction

_BINARY_OPS = set([opname for opname in dis.opname if opname.startswith('BINARY_')])


def _is_binary_opname(opname):
    return opname in _BINARY_OPS


_BINARY_OP_MAP = {
    'BINARY_POWER': '__pow__',
    'BINARY_MULTIPLY': '__mul__',
    'BINARY_MATRIX_MULTIPLY': '__matmul__',
    'BINARY_FLOOR_DIVIDE': '__floordiv__',
    'BINARY_TRUE_DIVIDE': '__div__',
    'BINARY_MODULO': '__mod__',
    'BINARY_ADD': '__add__',
    'BINARY_SUBTRACT': '__sub__',
    'BINARY_LSHIFT': '__lshift__',
    'BINARY_RSHIFT': '__rshift__',
    'BINARY_AND': '__and__',
    'BINARY_OR': '__or__',
    'BINARY_XOR': '__xor__',
    'BINARY_SUBSCR': '__getitem__',
    'BINARY_DIVIDE': '__div__'
}

_UNARY_OPS = set([opname for opname in dis.opname if opname.startswith('UNARY_')
                  and opname != 'UNARY_NOT'])


def _is_unary_opname(opname):
    return opname in _UNARY_OPS


_UNARY_OP_MAP = {
    'UNARY_POSITIVE': '__pos__',
    'UNARY_NEGATIVE': '__neg__',
    'UNARY_INVERT': '__invert__',
}

_CALL_OPNAMES = {
    'CALL_FUNCTION',
    'CALL_FUNCTION_KW',
    'CALL_FUNCTION_EX',
    'CALL_METHOD',
}


def _is_call_opname(opname):
    return opname in _CALL_OPNAMES


def get_stepping_variants(code):
    stk = []
    curr_line = -1
    for instruction in dis.get_instructions(code):
        if instruction.starts_line:
            curr_line = instruction.starts_line
        if _is_call_opname(instruction.opname):
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
                curr_line
            )
        elif _is_binary_opname(instruction.opname):
            yield Instruction(
                instruction.opname,
                instruction.opcode,
                instruction.arg,
                _BINARY_OP_MAP[instruction.opname],
                instruction.offset,
                curr_line
            )
        else:
            stk.append(instruction)
