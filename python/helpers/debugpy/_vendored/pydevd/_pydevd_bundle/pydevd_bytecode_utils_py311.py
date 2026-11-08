from _pydevd_bundle.pydevd_constants import IS_PY311_OR_GREATER
import dis
from types import CodeType
from collections import namedtuple

DEBUG = False

_Pos = namedtuple("_Pos", "lineno endlineno startcol endcol")


def _is_inside(item_pos: _Pos, container_pos: _Pos):
    if item_pos.lineno < container_pos.lineno or item_pos.endlineno > container_pos.endlineno:
        return False

    if item_pos.lineno == container_pos.lineno:
        if item_pos.startcol < container_pos.startcol:
            return False

    if item_pos.endlineno == container_pos.endlineno:
        if item_pos.endcol > container_pos.endcol:
            return False

    # Not outside, must be inside.
    return True


# JetBrains extension: the operators are smart step into targets too, as in the pydevd debugger of PyCharm.
_BINARY_OP_NAMES = {
    "+": "__add__",
    "-": "__sub__",
    "*": "__mul__",
    "@": "__matmul__",
    "/": "__truediv__",
    "//": "__floordiv__",
    "%": "__mod__",
    "**": "__pow__",
    "<<": "__lshift__",
    ">>": "__rshift__",
    "&": "__and__",
    "|": "__or__",
    "^": "__xor__",
    "+=": "__iadd__",
    "-=": "__isub__",
    "*=": "__imul__",
    "@=": "__imatmul__",
    "/=": "__itruediv__",
    "//=": "__ifloordiv__",
    "%=": "__imod__",
    "**=": "__ipow__",
    "<<=": "__ilshift__",
    ">>=": "__irshift__",
    "&=": "__iand__",
    "|=": "__ior__",
    "^=": "__ixor__",
}

_COMPARE_OP_NAMES = {
    "<": "__lt__",
    "<=": "__le__",
    "==": "__eq__",
    "!=": "__ne__",
    ">": "__gt__",
    ">=": "__ge__",
}

_UNARY_OP_NAMES = {
    "UNARY_NEGATIVE": "__neg__",
    "UNARY_POSITIVE": "__pos__",
    "UNARY_INVERT": "__invert__",
}

_INTRINSIC_UNARY_POSITIVE = 5


def _get_operator_name(instr):
    """
    JetBrains extension: the special method that an operator instruction calls, or None for another instruction.
    """
    if instr.opname == "BINARY_OP":
        return _BINARY_OP_NAMES.get(instr.argrepr)
    if instr.opname == "COMPARE_OP":
        return _COMPARE_OP_NAMES.get(instr.argval)
    if instr.opname == "CONTAINS_OP":
        return "__contains__"
    if instr.opname == "CALL_INTRINSIC_1" and instr.arg == _INTRINSIC_UNARY_POSITIVE:
        return "__pos__"
    return _UNARY_OP_NAMES.get(instr.opname)


def _get_compare_end(source, positions, index):
    """
    JetBrains extension: gives the end of the comparison number `index` of the comparison chain `source`.
    The comparison starts at the chain start and ends at its right operand, as `a < b` and `a < b < c` in `a < b < c`.

    :return: the end line and the end column in UTF-8 bytes, or None when `source` is not a comparison chain.
    """
    import ast

    try:
        # The parentheses let the chain span several lines.
        node = ast.parse("(" + source + ")", mode="eval").body
    except SyntaxError:
        return None
    if not isinstance(node, ast.Compare) or index >= len(node.comparators):
        return None

    comparator = node.comparators[index]
    if comparator.end_lineno == 1:
        # The first line of `source` starts at the start of the chain, after the added parenthesis.
        return positions.lineno, positions.col_offset + comparator.end_col_offset - 1
    return positions.lineno + comparator.end_lineno - 1, comparator.end_col_offset


def _get_smart_step_into_targets(code):
    import linecache
    from .pydevd_bytecode_utils import Target

    filename = code.co_filename

    targets_root = []
    children = []
    compare_counts = {}
    for instr in dis.Bytecode(code):
        if instr.opname == "LOAD_CONST":
            if isinstance(instr.argval, CodeType):
                children.append(_get_smart_step_into_targets(instr.argval))

        # JetBrains extension: a CALL_INTRINSIC_1 other than the unary plus calls no user code.
        elif instr.opname == "CALL" or _get_operator_name(instr) is not None:
            positions = instr.positions
            if positions.lineno is None:
                continue
            if positions.end_lineno is None:
                continue
            # JetBrains extension: `dis` gives the column offsets in UTF-8 bytes, so slice the encoded lines.
            lines = []
            for lineno in range(positions.lineno, positions.end_lineno + 1):
                lines.append(linecache.getline(filename, lineno).encode("utf-8"))

            startcol = positions.col_offset
            endcol = positions.end_col_offset

            if positions.lineno == positions.end_lineno:
                lines[0] = lines[0][startcol:endcol]
            else:
                lines[0] = lines[0][startcol:]
                lines[-1] = lines[-1][:endcol]

            source = b"".join(lines).decode("utf-8", "replace")
            endlineno = positions.end_lineno
            if instr.opname == "COMPARE_OP":
                # JetBrains extension: all the comparisons of a chain have the position of the chain.
                key = (positions.lineno, positions.end_lineno, startcol, endcol)
                index = compare_counts.get(key, 0)
                compare_counts[key] = index + 1
                endlineno, endcol = _get_compare_end(source, positions, index) or (endlineno, endcol)

            pos = _Pos(positions.lineno, endlineno, startcol, endcol)
            name = _get_operator_name(instr) or source
            targets_root.append(Target(name, positions.lineno, instr.offset, [], endlineno, startcol, endcol))

    for targets in children:
        for child_target in targets:
            pos = _Pos(child_target.lineno, child_target.endlineno, child_target.startcol, child_target.endcol)

            for outer_target in targets_root:
                outer_pos = _Pos(outer_target.lineno, outer_target.endlineno, outer_target.startcol, outer_target.endcol)
                if _is_inside(pos, outer_pos):
                    outer_target.children_targets.append(child_target)
                    break
    return targets_root


def calculate_smart_step_into_variants(frame, start_line, end_line, base=0):
    """
    Calculate smart step into variants for the given line range.
    :param frame:
    :type frame: :py:class:`types.FrameType`
    :param start_line:
    :param end_line:
    :return: A list of call names from the first to the last.
    :note: it's guaranteed that the offsets appear in order.
    :raise: :py:class:`RuntimeError` if failed to parse the bytecode or if dis cannot be used.
    """
    from .pydevd_bytecode_utils import _convert_target_to_variant

    variants = []
    code = frame.f_code
    lasti = frame.f_lasti

    call_order_cache = {}
    if DEBUG:
        print("dis.dis:")
        if IS_PY311_OR_GREATER:
            dis.dis(code, show_caches=False)
        else:
            dis.dis(code)

    for target in _get_smart_step_into_targets(code):
        variant = _convert_target_to_variant(target, start_line, end_line, call_order_cache, lasti, base)
        if variant is None:
            continue
        variants.append(variant)

    return variants
