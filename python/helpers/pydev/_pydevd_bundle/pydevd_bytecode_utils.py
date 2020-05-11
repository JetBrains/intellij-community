"""Bytecode analysing utils. Originally added for using in smart step into."""
import dis
import inspect
from collections import namedtuple

from _pydevd_bundle.pydevd_constants import IS_PY3K, IS_CPYTHON

__all__ = ["get_smart_step_into_candidates"]

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
    for opname in ('LOAD_CLASSDEREF', 'LOAD_METHOD'):
        _LOAD_OPNAMES.add(opname)
    for opname in ('CALL_FUNCTION_EX', 'CALL_METHOD'):
        _CALL_OPNAMES.add(opname)
else:
    _LOAD_OPNAMES.add('LOAD_LOCALS')
    for opname in ('CALL_FUNCTION_VAR', 'CALL_FUNCTION_VAR_KW'):
        _CALL_OPNAMES.add(opname)

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

_UNARY_OPS = set([opname for opname in dis.opname if opname.startswith('UNARY_') and opname != 'UNARY_NOT'])

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


# Similar to :py:class:`dis._Instruction` but without fields we don't use. Also :py:class:`dis._Instruction`
# is not available in Python 2.
Instruction = namedtuple("Instruction", ["opname", "opcode", "arg", "argval", "lineno", "offset"])

if IS_PY3K:
    long = int

try:
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
            yield (offset, op, arg)


def _code_to_name(inst):
    """If thw instruction's ``argval`` is :py:class:`types.CodeType`, replace it with the name and return the updated instruction.

    :type inst: :py:class:`Instruction`
    :rtype: :py:class:`Instruction`
    """
    if inspect.iscode(inst.argval):
        return inst._replace(argval=inst.argval.co_name)
    return inst


def get_smart_step_into_candidates(code):
    """Iterate through the bytecode and return a list of instructions which can be smart step into candidates.

    :param code: A code object where we searching for calls.
    :type code: :py:class:`types.CodeType`
    :return: list of :py:class:`~Instruction` that represents the objects that were called
      by one of the Python call instructions.
    :raise: :py:class:`RuntimeError` if failed to parse the bytecode.
    """
    if not IS_CPYTHON:
        # For implementations other than CPython we fall back to simple step into.
        return []

    linestarts = dict(dis.findlinestarts(code))
    varnames = code.co_varnames
    names = code.co_names
    constants = code.co_consts
    freevars = code.co_freevars
    lineno = None
    stk = []  # only the instructions related to calls are pushed in the stack
    result = []

    for offset, op, arg in _unpack_opargs(code.co_code):
        try:
            if linestarts is not None:
                lineno = linestarts.get(offset, None) or lineno
            opname = dis.opname[op]
            argval = None
            if arg is None:
                if _is_binary_opname(opname):
                    stk.pop()
                    result.append(Instruction(opname, op, arg, _BINARY_OP_MAP[opname], lineno, offset))
                elif _is_unary_opname(opname):
                    result.append(Instruction(opname, op, arg, _UNARY_OP_MAP[opname], lineno, offset))
            if opname == 'COMPARE_OP':
                stk.pop()
                cmp_op = dis.cmp_op[arg]
                if cmp_op not in ('exception match', 'BAD'):
                    result.append(Instruction(opname, op, arg, _COMP_OP_MAP[cmp_op], lineno, offset))
            if _is_load_opname(opname):
                if opname == 'LOAD_CONST':
                    argval = constants[arg]
                elif opname == 'LOAD_NAME' or opname == 'LOAD_GLOBAL':
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
                stk.append(Instruction(opname, op, arg, argval, lineno, offset))
            elif _is_make_opname(opname):
                tos = stk.pop()  # qualified name of the function or function code in Python 2
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
                argc = arg  # the number of the function or method arguments
                if opname == 'CALL_FUNCTION_KW' or not IS_PY3K and opname == 'CALL_FUNCTION_VAR':
                    stk.pop()  # pop the mapping or iterable with arguments or parameters
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
                result.append(tos._replace(offset=offset))  # the actual offset is not when a function was loaded but when it was called
        except:
            err_msg = "Bytecode parsing error at: offset(%d), opname(%s), arg(%d)" % (offset, dis.opname[op], arg)
            raise RuntimeError(err_msg)
    return result


Variant = namedtuple('Variant', ['name', 'is_visited'])


def calculate_smart_step_into_variants(frame, start_line, end_line):
    """
    Calculate smart step into variants for the given line range.
    :param frame:
    :type frame: :py:class:`types.FrameType`
    :param start_line:
    :param end_line:
    :return: A list of call names from the first to the last.
    :raise: :py:class:`RuntimeError` if failed to parse the bytecode.
    """
    variants = []
    is_context_reached = False
    code = frame.f_code
    lasti = frame.f_lasti
    for inst in get_smart_step_into_candidates(code):
        if inst.lineno and inst.lineno > end_line:
            break
        if not is_context_reached and inst.lineno is not None and inst.lineno >= start_line:
            is_context_reached = True
        if not is_context_reached:
            continue
        variants.append(Variant(inst.argval, inst.offset <= lasti))
    return variants


def find_last_func_call_order(frame, start_line):
    """Find the call order of the last function call between ``start_line`` and last executed instruction.

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
    lasti = frame.f_lasti
    for inst in get_smart_step_into_candidates(code):
        if inst.offset > lasti:
            break
        last_call_name = inst.argval

    return last_call_name
