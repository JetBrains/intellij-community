#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
from collections import namedtuple
from _pydevd_bundle.pydevd_constants import IS_PY311_OR_GREATER, IS_PY312_OR_GREATER
import dis

# A truncated version of the named tuple from the CPython `dis` module that contains
# only the fields necessary for the extraction of stepping variants.
Instruction = namedtuple(
    "_Instruction",
    [
        'opname',
        'opcode',
        'arg',
        'argval',
        'offset',
        'lineno',
    ]
)

def get_stepping_variants(code):
    stk = []
    curr_line = -1
    for instruction in dis.get_instructions(code):
        line = _get_current_line(instruction)
        if line != -1:
            curr_line = line

        reraise_command = _get_reraise_instruction(instruction, curr_line)
        if reraise_command is not None:
            yield reraise_command
        elif _is_call_opname(instruction.opname):
            call_command = _get_call_instruction(instruction, stk, curr_line)
            if call_command is not None:
                yield call_command
        elif _is_binary_opname(instruction.opname):
            yield _get_binary_instruction(instruction, curr_line)
        elif _is_unary_opname(instruction.opname):
            yield _get_unary_instruction(instruction, curr_line)
        elif instruction.opname == 'CALL_INTRINSIC_1' and instruction.arg == 5:
            # INTRINSIC_UNARY_POSITIVE from Python 3.12
            yield Instruction(
                instruction.opname,
                instruction.opcode,
                instruction.arg,
                '__pos__',
                instruction.offset,
                instruction.positions.lineno
            )
        elif _is_comp_opname(instruction.opname):
            comp_command = _get_comp_instruction(instruction, curr_line)
            if comp_command is not None:
                yield comp_command
        else:
            stk.append(instruction)


# --------------------- RERAISE_UTILS ---------------------
RERAISE_COMMAND = 'RERAISE'

def _get_reraise_instruction(instruction, curr_line):
    if instruction.opname == RERAISE_COMMAND:
        return Instruction(
            instruction.opname,
            instruction.opcode,
            instruction.arg,
            RERAISE_COMMAND,
            instruction.offset,
            curr_line
        )

    return None


# --------------------- BINARY_UTILS ---------------------
if IS_PY311_OR_GREATER:
    _BINARY_OP_MAP = {
        '**': '__pow__',
        '**=': '__ipow__',
        '*': '__mul__',
        '*=': '__imul__',
        '@': '__matmul__',
        '@=': '__imatmul__',
        '//': '__floordiv__',
        '//=': '__ifloordiv__',
        '/': '__div__',
        '/=': '__idiv__',
        '%': '__mod__',
        '%=': '__imod__',
        '+': '__add__',
        '+=': '__iadd__',
        '-': '__sub__',
        '-=': '__isub__',
        '<<': '__lshift__',
        '<<=': '__ilshift__',
        '>>': '__rshift__',
        '>>=': '__irshift__',
        '&': '__and__',
        '&=': '__iand__',
        '|': '__or__',
        '|=': '__ior__',
        '^': '__xor__',
        '^=': '__ixor__',
        'BINARY_SUBSCR': '__getitem__',
        'BINARY_SLICE': '__getitem__',
    }
else:
    _BINARY_OP_MAP = {
        'BINARY_POWER': '__pow__',
        'INPLACE_POWER': '__ipow__',
        'BINARY_MULTIPLY': '__mul__',
        'INPLACE_MULTIPLY': '__imul__',
        'BINARY_MATRIX_MULTIPLY': '__matmul__',
        'INPLACE_MATRIX_MULTIPLY': '__imatmul__',
        'BINARY_FLOOR_DIVIDE': '__floordiv__',
        'INPLACE_FLOOR_DIVIDE': '__ifloordiv__',
        'BINARY_TRUE_DIVIDE': '__div__',
        'INPLACE_TRUE_DIVIDE': '__idiv__',
        'BINARY_MODULO': '__mod__',
        'INPLACE_MODULO': '__imod__',
        'BINARY_ADD': '__add__',
        'INPLACE_ADD': '__iadd__',
        'BINARY_SUBTRACT': '__sub__',
        'INPLACE_SUBTRACT': '__isub__',
        'BINARY_LSHIFT': '__lshift__',
        'INPLACE_LSHIFT': '__ilshift__',
        'BINARY_RSHIFT': '__rshift__',
        'INPLACE_RSHIFT': '__irshift__',
        'BINARY_AND': '__and__',
        'INPLACE_AND': '__iand__',
        'BINARY_OR': '__or__',
        'INPLACE_OR': '__ior__',
        'BINARY_XOR': '__xor__',
        'INPLACE_XOR': '__ixor__',
        'BINARY_SUBSCR': '__getitem__',
        'BINARY_DIVIDE': '__div__'
    }

_BINARY_OPS = set([opname for opname in dis.opname if opname.startswith('BINARY_')])


def _is_binary_opname(opname):
    return opname in _BINARY_OPS


def _get_binary_instruction(instruction, curr_line):
    return Instruction(
        instruction.opname,
        instruction.opcode,
        instruction.arg,
        _BINARY_OP_MAP[
            instruction.argrepr if instruction.argrepr else instruction.opname],
        instruction.offset,
        curr_line
    )


# --------------------- UNARY_UTILS ---------------------
_UNARY_OP_MAP = {
    'UNARY_POSITIVE': '__pos__',
    'UNARY_NEGATIVE': '__neg__',
    'UNARY_INVERT': '__invert__',
}

_UNARY_OPS = set([opname for opname in dis.opname if opname.startswith('UNARY_')
                  and opname != 'UNARY_NOT'])


def _is_unary_opname(opname):
    return opname in _UNARY_OPS


def _get_unary_instruction(instruction, curr_line):
    return Instruction(
        instruction.opname,
        instruction.opcode,
        instruction.arg,
        _UNARY_OP_MAP[instruction.opname],
        instruction.offset,
        curr_line
    )


# --------------------- CALL_UTILS ---------------------
if IS_PY311_OR_GREATER:
    _CALL_OPNAMES = {
        'CALL',
        'CALL_FUNCTION_EX',
        'CALL_KW'
    }
else:
    _CALL_OPNAMES = {
        'CALL_FUNCTION',
        'CALL_FUNCTION_KW',
        'CALL_FUNCTION_EX',
        'CALL_METHOD',
    }


def _is_call_opname(opname):
    return opname in _CALL_OPNAMES


def _get_call_instruction(instruction, stk, curr_line=None):
    if instruction.opname == 'CALL_FUNCTION_EX':
        # The latest load instruction is the function arguments,
        # not its name.
        _remove_latest_load_instruction_from_stack(stk)
    while stk and not _is_load_opname(stk[-1].opname):
        stk.pop()
    if not stk:
        return None
    tos = stk.pop()
    return Instruction(
        tos.opname,
        tos.opcode,
        tos.arg,
        tos.argval,
        instruction.offset,
        curr_line if curr_line is not None else tos.positions.lineno
    )


# --------------------- COMP_UTILS ---------------------
_COMP_OP_MAP = {
    '<': '__lt__',
    '<=': '__le__',
    '==': '__eq__',
    '!=': '__ne__',
    '>': '__gt__',
    '>=': '__ge__',
    'in': '__contains__',
    'not in': '__contains__',
}


def _is_comp_opname(opname):
    return opname == 'COMPARE_OP'


def _get_comp_instruction(instruction, curr_line=-1):
    if IS_PY312_OR_GREATER:
        try:
            cmp_op = instruction.argval
        except:
            cmp_op = dis.cmp_op[instruction.arg]
    else:
        cmp_op = dis.cmp_op[instruction.arg]

    if cmp_op not in ('exception match', 'BAD'):
        return Instruction(
            instruction.opname,
            instruction.opcode,
            instruction.arg,
            _COMP_OP_MAP[cmp_op],
            instruction.offset,
            curr_line
        )
    return None


# --------------------- LOAD_UTILS ---------------------
_LOAD_OP_NAMES = {
    'LOAD_NAME',
    'LOAD_GLOBAL',
    'LOAD_ATTR',
    'LOAD_METHOD',
    'LOAD_DEREF',
    'LOAD_FAST',
}


def _is_load_opname(opname):
    return opname in _LOAD_OP_NAMES


# --------------------- UTILS ---------------------
def _remove_latest_load_instruction_from_stack(stk):
    """Remove the latest load instruction from the stack.

    Note that this function removes everything before the latest load instruction.
    """
    while stk and not _is_load_opname(stk[-1].opname):
        stk.pop()
    if stk:
        # The instruction loading the arguments is found, pop it.
        stk.pop()


if IS_PY311_OR_GREATER:
    def _get_current_line(instruction):
        return instruction.positions.lineno
else:
    def _get_current_line(instruction):
        if instruction.starts_line:
            return instruction.starts_line
        return -1