"""Bytecode analyzing utils that are primarily used for extracting smart steps into
variants."""

import dis
import inspect
import logging
import os
import sys
from collections import namedtuple

from _pydevd_bundle.pydevd_constants import IS_PY3K, IS_CPYTHON, IS_PY312_OR_GREATER

__all__ = [
    'find_last_call_name',
    'find_last_func_call_order',
    'get_smart_step_into_candidates',
]

_logger = logging.getLogger(__name__)
DEBUG = os.environ.get('PYCHARM_SMART_STEP_INTO_DEBUG', 'False') == 'True'
if DEBUG:
    _logger.setLevel(logging.DEBUG)
    handler = logging.StreamHandler()
    formatter = logging.Formatter('%(name)s:%(levelname)s:%(message)s')
    handler.setFormatter(formatter)
    _logger.addHandler(handler)

_LOAD_OPNAMES = {
    'LOAD_BUILD_CLASS',
    'LOAD_CONST',
    'LOAD_NAME',
    'LOAD_ATTR',
    'LOAD_GLOBAL',
    'LOAD_FAST',
    'LOAD_CLOSURE',
    'LOAD_DEREF',
}

_CALL_OPNAMES = {
    'CALL_FUNCTION',
    'CALL_FUNCTION_KW',
}

if IS_PY3K:
    for each_opname in ('LOAD_CLASSDEREF', 'LOAD_METHOD'):
        _LOAD_OPNAMES.add(each_opname)
    for each_opname in ('CALL_FUNCTION_EX', 'CALL_METHOD'):
        _CALL_OPNAMES.add(each_opname)
    if IS_PY312_OR_GREATER:
        _CALL_OPNAMES.add('CALL')
else:
    _LOAD_OPNAMES.add('LOAD_LOCALS')
    for each_opname in ('CALL_FUNCTION_VAR', 'CALL_FUNCTION_VAR_KW'):
        _CALL_OPNAMES.add(each_opname)

_BINARY_OPS = set([opname for opname in dis.opname if opname.startswith('BINARY_')])

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
}

if not IS_PY3K:
    _BINARY_OP_MAP['BINARY_DIVIDE'] = '__div__'

_UNARY_OPS = set([opname for opname in dis.opname if opname.startswith('UNARY_')
                  and opname != 'UNARY_NOT'])

_UNARY_OP_MAP = {
    'UNARY_POSITIVE': '__pos__',
    'UNARY_NEGATIVE': '__neg__',
    'UNARY_INVERT': '__invert__',
}

_MAKE_OPS = set([opname for opname in dis.opname if opname.startswith('MAKE_')])

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


def _is_load_opname(opname):
    return opname in _LOAD_OPNAMES


def _is_call_opname(opname):
    return opname in _CALL_OPNAMES


def _is_binary_opname(opname):
    return opname in _BINARY_OPS


def _is_unary_opname(opname):
    return opname in _UNARY_OPS


def _is_make_opname(opname):
    return opname in _MAKE_OPS


# A truncated version of the named tuple from the CPython `dis` module that contains
# only fields necessary for the extraction of stepping variants.
_Instruction = namedtuple(
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

if IS_PY3K:
    long = int

try:
    # noinspection PyProtectedMember,PyUnresolvedReferences
    _unpack_opargs = dis._unpack_opargs
except AttributeError:
    def _unpack_opargs(code):
        n = len(code)
        i = 0
        extended_arg = 0
        while i < n:
            c = code[i]
            op = ord(c)
            offset = i
            arg = None
            i += 1
            if op >= dis.HAVE_ARGUMENT:
                arg = ord(code[i]) + ord(code[i + 1]) * 256 + extended_arg
                extended_arg = 0
                i += 2
                if op == dis.EXTENDED_ARG:
                    extended_arg = arg * long(65536)
            yield offset, op, arg


def _code_to_name(inst):
    """If the instruction's ``argval`` is :py:class:`types.CodeType`,
    replace it with the name and return the updated instruction.

    :type inst: :py:class:`_Instruction`
    :rtype: :py:class:`_Instruction`
    """
    if inspect.iscode(inst.argval):
        # noinspection PyProtectedMember
        return inst._replace(argval=inst.argval.co_name)
    return inst


class _BytecodeParsingError(Exception):
    """Raised when fail of bytecode parsing."""
    def __init__(self, offset, opname, arg):
        self.offset = offset
        self.opname = opname
        self.arg = arg

    def __str__(self):
        return "Bytecode parsing error at: offset(%d), opname(%s), arg(%d)" % (
            self.offset, self.opname, self.arg)


def _get_smart_step_into_candidates(code):
    """Iterate through the bytecode and return a list of instructions which can be
    smart step into candidates.

    :param code: A code object where we are searching for calls.
    :type code: :py:class:`types.CodeType`
    :return: list of :py:class:`~_Instruction` that represents the objects that were
      called by one of the Python call instructions.
    :raise: :py:class:`_BytecodeParsingError` if failed to parse the bytecode.
    """
    if not IS_CPYTHON:
        # For implementations other than CPython we fall back to simple step into.
        _logger.debug('Not CPython, returning the empty list of candidates')
        return []

    linestarts = dict(dis.findlinestarts(code))
    _logger.debug('Instructions stating lines: {}'.format(linestarts))

    varnames = code.co_varnames
    _logger.debug('Local variables in function: {}'.format(varnames))

    names = code.co_names
    _logger.debug('Named used by bytecode in function: {}'.format(names))

    constants = code.co_consts
    _logger.debug('Literals used by bytecode in function: {}'.format(constants))

    freevars = code.co_freevars
    _logger.debug('Name of free variables in function: {}'.format(freevars))

    lineno = None
    stk = []  # Only the instructions related to calls are pushed in the stack.
    result = []

    for offset, op, arg in _unpack_opargs(code.co_code):
        _logger.debug('-------------------------')
        _logger.debug('Current instruction stack: {}'.format(stk))
        _logger.debug('offset[{}] - op[{}={}] - arg[{}]'.format(
            offset, op, dis.opname[op], arg))
        try:
            if linestarts is not None:
                lineno = linestarts.get(offset, None)
            opname = dis.opname[op]
            argval = None
            if arg is None:
                if _is_binary_opname(opname):
                    _logger.debug('Handling binary operator')
                    stk.pop()
                    result.append(_Instruction(
                        opname, op, arg, _BINARY_OP_MAP[opname], offset, lineno))
                elif _is_unary_opname(opname):
                    _logger.debug('Handling unary operator')
                    result.append(_Instruction(
                        opname, op, arg, _UNARY_OP_MAP[opname], offset, lineno))
            if opname == 'COMPARE_OP':
                _logger.debug('Handling comparison operation')
                stk.pop()
                cmp_op = dis.cmp_op[arg]
                if cmp_op not in ('exception match', 'BAD'):
                    result.append(_Instruction(
                        opname, op, arg, _COMP_OP_MAP[cmp_op], offset, lineno))
            if _is_load_opname(opname):
                _logger.debug('Handling load operation')
                if opname == 'LOAD_CONST':
                    argval = constants[arg]
                elif opname == 'LOAD_NAME' or opname == 'LOAD_GLOBAL':
                    if IS_PY312_OR_GREATER:
                        argval = names[arg >> 1]
                    else:
                        argval = names[arg]
                elif opname == 'LOAD_ATTR':
                    stk.pop()
                    argval = names[arg]
                elif opname == 'LOAD_FAST':
                    argval = varnames[arg]
                elif IS_PY3K and opname == 'LOAD_METHOD':
                    stk.pop()
                    argval = names[arg]
                elif opname == 'LOAD_DEREF':
                    argval = freevars[arg]
                stk.append(_Instruction(opname, op, arg, argval, offset, lineno))
            elif _is_make_opname(opname):
                # Qualified name of the function or function code in Python 2.
                _logger.debug('Handling make operation')
                tos = stk.pop()
                argc = 0
                if IS_PY3K:
                    stk.pop()  # function code
                    for flag in (0x01, 0x02, 0x04, 0x08):
                        if arg & flag:
                            argc += 1  # each flag means one extra element to pop
                else:
                    argc = arg
                    tos = _code_to_name(tos)
                while argc > 0:
                    stk.pop()
                    argc -= 1
                stk.append(tos)
            elif _is_call_opname(opname):
                _logger.debug('Handling call operation')
                argc = arg  # the number of the function or method arguments
                if (opname == 'CALL_FUNCTION_KW' or not IS_PY3K
                        and opname == 'CALL_FUNCTION_VAR'):
                    # Pop the mapping or iterable with arguments or parameters.
                    stk.pop()
                elif not IS_PY3K and opname == 'CALL_FUNCTION_VAR_KW':
                    stk.pop()  # pop the mapping with arguments
                    stk.pop()  # pop the iterable with parameters
                elif not IS_PY3K and opname == 'CALL_FUNCTION':
                    argc = arg & 0xff  # positional args
                    argc += ((arg >> 8) * 2)  # keyword args
                elif opname == 'CALL_FUNCTION_EX':
                    has_keyword_args = arg & 0x01
                    if has_keyword_args:
                        stk.pop()
                    stk.pop()  # positional args
                    argc = 0
                while argc > 0:
                    stk.pop()  # popping args from the stack
                    argc -= 1
                tos = _code_to_name(stk[-1])
                if tos.opname == 'LOAD_BUILD_CLASS':
                    # an internal `CALL_FUNCTION` for building a class
                    continue
                # The actual offset is not when a function was loaded but when
                # it was called.
                _logger.debug('Adding call target from the top of the stack: {}'.
                              format(tos))
                result.append(tos._replace(offset=offset))
        except Exception as e:
            err = _BytecodeParsingError(offset, dis.opname[op], arg)
            err.__cause__ = e
            raise err
    return result


def _get_smart_step_into_candidates_312(code):
    result = []
    stk = []
    for instruction in dis.get_instructions(code):
        if instruction.opname == 'CALL':
            if stk[-1].opname == 'PUSH_NULL':
                stk.pop()
            tos = stk[-1]
            if tos.opname == 'LOAD_GLOBAL':
                result.append(_Instruction(
                    tos.opname,
                    tos.opcode,
                    tos.arg,
                    tos.argval,
                    tos.offset,
                    tos.positions.lineno
                ))
        else:
            stk.append(instruction)
    return result


if IS_PY312_OR_GREATER:
    get_smart_step_into_candidates = _get_smart_step_into_candidates_312
else:
    get_smart_step_into_candidates = _get_smart_step_into_candidates


Variant = namedtuple('Variant', ['name', 'is_visited'])


def find_stepping_targets(frame, start_line, end_line):
    """Find the *ordered* stepping targets within the given line range."""
    _logger.debug("Collecting stepping targets for '{}' and line range [{}, {}]".format(
        frame.f_code.co_name, start_line, end_line))
    if DEBUG:
        _logger.debug('The disassembled version of the code:')
        dis.dis(frame.f_code, file=sys.stderr)
    stepping_targets = []
    is_within_range = False
    code = frame.f_code
    last_instruction = frame.f_lasti
    _logger.debug('Last executed instruction offset is {}'.format(last_instruction))
    for inst in get_smart_step_into_candidates(code):
        _logger.debug('Checking {} smart step into candidate'.format(inst))
        if inst.lineno and inst.lineno > end_line:
            break
        if (not is_within_range and inst.lineno is not None
                and inst.lineno >= start_line):
            is_within_range = True
        if not is_within_range:
            continue
        stepping_targets.append(Variant(inst.argval, inst.offset < last_instruction))
    _logger.debug('Found stepping targets: {}'.format(stepping_targets))
    return stepping_targets


def find_last_func_call_order(frame, start_line):
    """Find the call order of the last function call between ``start_line``
    and last executed instruction.

    :param frame: A frame inside which we are looking the function call.
    :type frame: :py:class:`types.FrameType`
    :param start_line:
    :return: call order or -1 if we fail to find the call order for some
      reason.
    :rtype: int
    :raise: :py:class:`RuntimeError` if failed to parse the bytecode.
    """
    code = frame.f_code
    lasti = frame.f_lasti
    cache = {}
    call_order = -1
    for inst in get_smart_step_into_candidates(code):
        if inst.offset > lasti:
            break
        if inst.lineno >= start_line:
            name = inst.argval
            call_order = cache.setdefault(name, -1)
            call_order += 1
            cache[name] = call_order
    return call_order


def find_last_call_name(frame):
    """Find the name of the last call made in the frame.

    :param frame: A frame inside which we are looking the last call.
    :type frame: :py:class:`types.FrameType`
    :return: The name of a function or method that has been called last.
    :rtype: str
    :raise: :py:class:`RuntimeError` if failed to parse the bytecode.
    """
    last_call_name = None
    code = frame.f_code
    last_instruction = frame.f_lasti
    for inst in get_smart_step_into_candidates(code):
        if inst.offset > last_instruction:
            break
        last_call_name = inst.argval

    return last_call_name
