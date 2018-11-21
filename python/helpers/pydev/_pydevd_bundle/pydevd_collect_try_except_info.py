from opcode import HAVE_ARGUMENT, EXTENDED_ARG, hasconst, opname, hasname, hasjrel, haslocal, \
    hascompare, hasfree, cmp_op
import dis
import sys
from collections import namedtuple

try:
    xrange
except NameError:
    xrange = range


class TryExceptInfo(object):

    def __init__(self, try_line, is_finally=False):
        self.try_line = try_line
        self.is_finally = is_finally
        self.except_line = -1
        self.except_bytecode_offset = -1
        self.except_end_line = -1
        self.except_end_bytecode_offset = -1
        self.raise_lines_in_except = []

    def is_line_in_try_block(self, line):
        return self.try_line <= line <= self.except_line

    def is_line_in_except_block(self, line):
        return self.except_line <= line <= self.except_end_line

    def __str__(self):
        lst = [
            '{try:',
            str(self.try_line),
            ' except ',
            str(self.except_line),
            ' end block ',
            str(self.except_end_line),
        ]
        if self.raise_lines_in_except:
            lst.append(' raises: %s' % (', '.join(str(x) for x in self.raise_lines_in_except),))

        lst.append('}')
        return ''.join(lst)

    __repr__ = __str__


def _get_line(op_offset_to_line, op_offset, firstlineno, search=False):
    op_offset_original = op_offset
    while op_offset >= 0:
        ret = op_offset_to_line.get(op_offset)
        if ret is not None:
            return ret - firstlineno
        if not search:
            return ret
        else:
            op_offset -= 1
    raise AssertionError('Unable to find line for offset: %s.Info: %s' % (
        op_offset_original, op_offset_to_line))


def debug(s):
    pass


_Instruction = namedtuple('_Instruction', 'opname, opcode, starts_line, argval, is_jump_target, offset')


def _iter_as_bytecode_as_instructions_py2(co):
    code = co.co_code
    op_offset_to_line = dict(dis.findlinestarts(co))
    labels = set(dis.findlabels(code))
    bytecode_len = len(code)
    i = 0
    extended_arg = 0
    free = None

    op_to_name = opname

    while i < bytecode_len:
        c = code[i]
        op = ord(c)
        is_jump_target = i in labels

        curr_op_name = op_to_name[op]
        initial_bytecode_offset = i

        i = i + 1
        if op < HAVE_ARGUMENT:
            yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), None, is_jump_target, initial_bytecode_offset)

        else:
            oparg = ord(code[i]) + ord(code[i + 1]) * 256 + extended_arg

            extended_arg = 0
            i = i + 2
            if op == EXTENDED_ARG:
                extended_arg = oparg * 65536

            if op in hasconst:
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), co.co_consts[oparg], is_jump_target, initial_bytecode_offset)
            elif op in hasname:
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), co.co_names[oparg], is_jump_target, initial_bytecode_offset)
            elif op in hasjrel:
                argval = i + oparg
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), argval, is_jump_target, initial_bytecode_offset)
            elif op in haslocal:
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), co.co_varnames[oparg], is_jump_target, initial_bytecode_offset)
            elif op in hascompare:
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), cmp_op[oparg], is_jump_target, initial_bytecode_offset)
            elif op in hasfree:
                if free is None:
                    free = co.co_cellvars + co.co_freevars
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), free[oparg], is_jump_target, initial_bytecode_offset)
            else:
                yield _Instruction(curr_op_name, op, _get_line(op_offset_to_line, initial_bytecode_offset, 0), oparg, is_jump_target, initial_bytecode_offset)


def collect_try_except_info(co, use_func_first_line=False):
    if not hasattr(co, 'co_lnotab'):
        return []

    if use_func_first_line:
        firstlineno = co.co_firstlineno
    else:
        firstlineno = 0

    try_except_info_lst = []
    stack_in_setup = []

    if sys.version_info[0] < 3:
        iter_in = _iter_as_bytecode_as_instructions_py2(co)
    else:
        iter_in = dis.Bytecode(co)
    iter_in = list(iter_in)

    op_offset_to_line = dict(dis.findlinestarts(co))
    bytecode_to_instruction = {}
    for instruction in iter_in:
        bytecode_to_instruction[instruction.offset] = instruction

    if iter_in:
        for instruction in iter_in:
            curr_op_name = instruction.opname

            if curr_op_name == 'SETUP_EXCEPT':
                try_except_info = TryExceptInfo(
                    _get_line(op_offset_to_line, instruction.offset, firstlineno, search=True))
                try_except_info.except_bytecode_offset = instruction.argval
                try_except_info.except_line = _get_line(
                    op_offset_to_line,
                    try_except_info.except_bytecode_offset,
                    firstlineno,
                )

                stack_in_setup.append(try_except_info)

            elif curr_op_name == 'SETUP_FINALLY':
                # We need to collect try..finally blocks too to make sure that
                # the stack_in_setup we're using to collect info is correct.
                try_except_info = TryExceptInfo(
                    _get_line(op_offset_to_line, instruction.offset, firstlineno, search=True), is_finally=True)
                stack_in_setup.append(try_except_info)

            elif curr_op_name == 'RAISE_VARARGS':
                # We want to know about reraises and returns inside of except blocks (unfortunately
                # a raise appears to the debugger as a return, so, we may need to differentiate).
                if instruction.argval == 0:
                    for info in stack_in_setup:
                        info.raise_lines_in_except.append(
                            _get_line(op_offset_to_line, instruction.offset, firstlineno, search=True))

            elif curr_op_name == 'END_FINALLY':  # The except block also ends with 'END_FINALLY'.
                stack_in_setup[-1].except_end_bytecode_offset = instruction.offset
                stack_in_setup[-1].except_end_line = _get_line(op_offset_to_line, instruction.offset, firstlineno, search=True)
                if not stack_in_setup[-1].is_finally:
                    # Don't add try..finally blocks.
                    try_except_info_lst.append(stack_in_setup[-1])
                del stack_in_setup[-1]

        while stack_in_setup:
            # On Py3 the END_FINALLY may not be there (so, the end of the function is also the end
            # of the stack).
            stack_in_setup[-1].except_end_bytecode_offset = instruction.offset
            stack_in_setup[-1].except_end_line = _get_line(op_offset_to_line, instruction.offset, firstlineno, search=True)
            if not stack_in_setup[-1].is_finally:
                # Don't add try..finally blocks.
                try_except_info_lst.append(stack_in_setup[-1])
            del stack_in_setup[-1]

    return try_except_info_lst
