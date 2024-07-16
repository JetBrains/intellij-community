#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import dis
from opcode import HAVE_ARGUMENT, opname, EXTENDED_ARG, hasconst, hasname, hasjrel, \
    haslocal, hascompare, cmp_op, hasfree

from _pydevd_bundle.smart_step_into.pydevd_smart_step_into_util import Instruction


def _is_call_opname(opname):
    return opname.startswith('CALL_')


_LOAD_OPNAMES = {
    'LOAD_GLOBAL',
    'LOAD_ATTR',
    'LOAD_FAST',
    'LOAD_NAME',
    'LOAD_DEREF',
    'LOAD_CLOSURE',
}


def _is_load_opname(opname):
    return opname in _LOAD_OPNAMES


def get_stepping_variants(code):
    stk = []
    for instruction in get_instructions(code):
        if _is_call_opname(instruction.opname):
            while stk and not _is_load_opname(stk[-1].opname):
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
                tos.lineno
            )
        else:
            stk.append(instruction)


def get_instructions(co):
    code = co.co_code
    linestarts = dict(dis.findlinestarts(co))
    curr_line = -1
    n = len(code)
    i = 0
    extended_arg = 0
    free = None
    while i < n:
        c = code[i]
        op = ord(c)
        if i in linestarts:
            curr_line = linestarts[i]
        offset = i
        i = i + 1
        oparg = argval = ''
        if op >= HAVE_ARGUMENT:
            oparg = ord(code[i]) + ord(code[i + 1]) * 256 + extended_arg
            extended_arg = 0
            i = i+2
            if op == EXTENDED_ARG:
                extended_arg = oparg*65536L
            if op in hasconst:
                argval = repr(co.co_consts[oparg])
            elif op in hasname:
                argval = co.co_names[oparg]
            elif op in hasjrel:
                argval = repr(i + oparg)
            elif op in haslocal:
                argval = co.co_varnames[oparg]
            elif op in hascompare:
                argval = cmp_op[oparg]
            elif op in hasfree:
                if free is None:
                    free = co.co_cellvars + co.co_freevars
                argval = free[oparg]
        yield Instruction(
            opname[op],
            op,
            oparg,
            argval,
            offset,
            curr_line
        )
