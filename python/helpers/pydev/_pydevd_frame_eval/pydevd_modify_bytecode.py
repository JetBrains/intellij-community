import dis
import sys
import traceback
from collections import namedtuple
from opcode import opmap, EXTENDED_ARG, HAVE_ARGUMENT
from types import CodeType

MAX_BYTE = 255
MAX_DELTA = 254
RETURN_VALUE_SIZE = 2
IS_PY310_OR_GREATER = sys.version_info > (3, 10)


def _add_attr_values_from_insert_to_original(original_code, insert_code, insert_code_list, attribute_name, op_list):
    """
    This function appends values of the attribute `attribute_name` of the inserted code to the original values,
     and changes indexes inside inserted code. If some bytecode instruction in the inserted code used to call argument
     number i, after modification it calls argument n + i, where n - length of the values in the original code.
     So it helps to avoid variables mixing between two pieces of code.

    :param original_code: code to modify
    :param insert_code: code to insert
    :param insert_code_obj: bytes sequence of inserted code, which should be modified too
    :param attribute_name: name of attribute to modify ('co_names', 'co_consts' or 'co_varnames')
    :param op_list: sequence of bytecodes whose arguments should be changed
    :return: modified bytes sequence of the code to insert and new values of the attribute `attribute_name` for
    original code
    """
    orig_value = getattr(original_code, attribute_name)
    insert_value = getattr(insert_code, attribute_name)
    orig_names_len = len(orig_value)
    code_with_new_values = list(insert_code_list)
    offset = 0
    while offset < len(code_with_new_values):
        op = code_with_new_values[offset]
        if op in op_list:
            new_val = code_with_new_values[offset + 1] + orig_names_len
            if new_val > MAX_BYTE:
                code_with_new_values[offset + 1] = new_val & MAX_BYTE
                code_with_new_values = code_with_new_values[:offset] + [EXTENDED_ARG, new_val >> 8] + \
                                       code_with_new_values[offset:]
                offset += 2
            else:
                code_with_new_values[offset + 1] = new_val
        offset += 2
    new_values = orig_value + insert_value
    return bytes(code_with_new_values), new_values


def _modify_new_lines(code_to_modify, all_inserted_code):
    if code_to_modify.co_firstlineno == 1 and len(all_inserted_code) > 0 \
            and all_inserted_code[0].offset == 0 \
            and code_to_modify.co_name == '<module>':
        # There's a peculiarity here: if a breakpoint is added in the first line of
        # a module, we can't replace the code because we require a line event to stop
        # and the live event was already generated, so, fallback to tracing.
        return None

    # Python 3.10 and above uses a different schema for encoding of line numbers.
    # See PEP 626 for the details.
    if IS_PY310_OR_GREATER:
        return _make_linetable(code_to_modify, all_inserted_code)
    return _make_lnotab(code_to_modify, all_inserted_code)


def _make_linetable(code_to_modify, all_inserted_code):
    """A line table generator for Python 3.10 and above.

    Note that the PEP states that the format of the ``co_linetable`` attribute is
    opaque and may be changed in the future. However, we have to deal with it because
    a linetable is required for creating new code objects.

    .. seealso::

       :py:func:`~_make_lnotab`
       `PEP 626`_
       `lnotab_notes.txt`_

    .. _PEP 626: https://www.python.org/dev/peps/pep-0626/
    .. _lnotab_notes.txt: https://github.com/python/cpython/blob/5a14f71fe869d4a62dcdeb9a8fbbb5884c75060c/Objects/lnotab_notes.txt

    """
    new_line_table, end = [], 0
    inst_increments = code_to_modify.co_linetable[0::2]
    line_increments = code_to_modify.co_linetable[1::2]
    it = zip(inst_increments, line_increments)
    offset_to_code_list = {offset: code_list for offset, code_list in all_inserted_code}
    if 0 in offset_to_code_list:
        new_line_table.append(len(offset_to_code_list.pop(0)))
        new_line_table.append(0)
    for inst_delta, line_delta in it:
        start = end
        end = start + inst_delta
        if end in offset_to_code_list:
            new_delta = inst_delta + len(offset_to_code_list[end])
            _update_line_table_max_delta_aware(new_line_table, new_delta, line_delta)
        else:
            new_delta = inst_delta
            # In a general case, an injected `EXTENDED_ARG` instruction is not aligned
            # with the end of the previous or start of the current line.
            for off in offset_to_code_list.keys():
                if start < off <= end:
                    new_delta = inst_delta + len(offset_to_code_list[off])
                    break
            _update_line_table_max_delta_aware(new_line_table, new_delta, line_delta)
    return bytes(new_line_table)


def _update_line_table_max_delta_aware(line_table, inst_delta, line_delta):
    while inst_delta > MAX_DELTA:
        line_table.append(MAX_DELTA)
        line_table.append(line_delta)
        line_delta = 0
        inst_delta -= MAX_DELTA
    line_table.append(inst_delta)
    line_table.append(line_delta)


def _make_lnotab(code_to_modify, all_inserted_code):
    """
    Generate a new bytecode instruction to line number mapping aka ``lnotab`` after injecting the debugger specific code.
    Note, that the bytecode inserted should be the last instruction of the line preceding a line with the breakpoint.

    :param code_to_modify: the original code in which we injected new instructions.
    :type code_to_modify: :py:class:`types.CodeType`

    :param all_inserted_code: a list of modifications done. Each modification is given as a named tuple with
      the first field ``offset`` which is the instruction offset and ``code_list`` which is the list of instructions
      have been injected.
    :type all_inserted_code: list

    :return: bytes sequence of code with updated lines offsets which can be passed as the ``lnotab`` parameter of the
      :py:class:`types.CodeType` constructor.
    """
    # There's a nice overview of co_lnotab in
    # https://github.com/python/cpython/blob/3.6/Objects/lnotab_notes.txt
    new_list = list(code_to_modify.co_lnotab)
    if not new_list:
        # Could happen on a lambda (in this case, a breakpoint in the lambda should fallback to
        # tracing).
        return None

    byte_increments = code_to_modify.co_lnotab[0::2]
    line_increments = code_to_modify.co_lnotab[1::2]
    all_inserted_code = sorted(all_inserted_code, key=lambda x: x.offset)

    # As all numbers are relative, what we want is to hide the code we inserted in the previous line
    # (it should be the last thing right before we increment the line so that we have a line event
    # right after the inserted code).
    curr_line_offset_in_old_code = 0
    prev_line_offset_in_old_code = 0

    it = zip(byte_increments, line_increments)
    k = inserted_so_far = index_delta = 0
    for i, (byte_incr, line_incr) in enumerate(it):
        curr_line_offset_in_old_code += byte_incr

        # Skip if a line of code compiles to more than 127 instructions.
        # For example, 130 instructions - [255, 0, 5, 1]. Then skip [255, 0].
        if byte_incr == MAX_BYTE and line_incr == 0:
            continue

        current_command_offsets_range_in_new_code = range(prev_line_offset_in_old_code + inserted_so_far, curr_line_offset_in_old_code + inserted_so_far)

        if curr_line_offset_in_old_code == all_inserted_code[k].offset - inserted_so_far or\
                all_inserted_code[k].offset in current_command_offsets_range_in_new_code:
            bytecode_delta = len(all_inserted_code[k].code_list)
            inserted_so_far += bytecode_delta

            new_list_offset_index = i * 2 + index_delta
            expected_offset = new_list[new_list_offset_index] + bytecode_delta
            # For example, if [..., 254, 1, ...] and bytecode_delta == 12,
            # then lnotab should be modified to [..., 255, 0, 11, 1, ...].
            if expected_offset > MAX_BYTE:
                new_list = new_list[:new_list_offset_index] + [MAX_BYTE, 0] + new_list[new_list_offset_index:]
                index_delta += 2
                new_list[new_list_offset_index + 2] = expected_offset - MAX_BYTE
            else:
                new_list[new_list_offset_index] += bytecode_delta
            k += 1

        if k >= len(all_inserted_code):
            break

        prev_line_offset_in_old_code = curr_line_offset_in_old_code

    return bytes(new_list)


def _unpack_opargs(code, inserted_code_list, current_index):
    """
    Modified version of `_unpack_opargs` function from module `dis`.
    We have to use it, because sometimes code can be in an inconsistent state: if EXTENDED_ARG
    operator was introduced into the code, but it hasn't been inserted into `code_list` yet.
    In this case we can't use standard `_unpack_opargs` and we should check whether there are
    some new operators in `inserted_code_list`.
    """
    extended_arg = 0
    for i in range(0, len(code), 2):
        op = code[i]
        if op >= HAVE_ARGUMENT:
            if extended_arg == 0:
                # In case if we added EXTENDED_ARG, but haven't inserted it to the source code yet.
                # XXX: it seems like this procedure does not support multiple EXTENDED_ARG instruction.
                for code_index in range(current_index, len(inserted_code_list)):
                    inserted_offset, inserted_code = inserted_code_list[code_index]
                    if inserted_offset == i and inserted_code[0] == EXTENDED_ARG:
                        extended_arg = inserted_code[1] << 8
            arg = code[i + 1] | extended_arg
            extended_arg = (arg << 8) if op == EXTENDED_ARG else 0
        else:
            arg = None
        yield i, op, arg


def _update_label_offsets(code_obj, breakpoint_offset, breakpoint_code_list):
    """
    Update labels for the relative and absolute jump targets
    :param code_obj: code to modify
    :param breakpoint_offset: offset for the inserted code
    :param breakpoint_code_list: list of bytes to insert
    :return: bytes sequence with modified labels; list of named tuples (resulting offset, list of code instructions) with
      information about all inserted pieces of code
    """
    all_inserted_code = []
    InsertedCode = namedtuple('InsertedCode', ['offset', 'code_list'])
    # the list with all inserted pieces of code
    all_inserted_code.append(InsertedCode(breakpoint_offset, breakpoint_code_list))
    code_list = list(code_obj)
    j = 0

    while j < len(all_inserted_code):
        current_offset, current_code_list = all_inserted_code[j]
        offsets_for_modification = []

        # We iterate through the code, find all the jump instructions and update the labels they are pointing to.
        # There is no reason to update anything other than jumps because only jumps are affected by code injections.
        for offset, op, arg in _unpack_opargs(code_list, all_inserted_code, j):
            if arg is not None:
                if op in dis.hasjrel:
                    # has relative jump target
                    label = offset + 2 + arg
                    # reminder: current offset is the place where we inject code
                    if offset < current_offset < label:
                        # change labels for relative jump targets if code was inserted between the instruction and the jump label
                        offsets_for_modification.append(offset)
                elif op in dis.hasjabs:
                    # change label for absolute jump if code was inserted before it
                    if IS_PY310_OR_GREATER and current_offset // 2 < arg:
                        offsets_for_modification.append(offset)
                    elif current_offset < arg:
                        offsets_for_modification.append(offset)
        for i in offsets_for_modification:
            op = code_list[i]
            if op >= dis.HAVE_ARGUMENT:
                # CPython 3.10 and upwards use words instead of bytes for offsets!
                adjust = len(current_code_list) // 2 if IS_PY310_OR_GREATER \
                    else len(current_code_list)
                new_arg = code_list[i + 1] + adjust
                if new_arg <= MAX_BYTE:
                    code_list[i + 1] = new_arg
                else:
                    # handle bytes overflow
                    if i - 2 > 0 and code_list[i - 2] == EXTENDED_ARG and code_list[i - 1] < MAX_BYTE:
                        # if new argument > 255 and EXTENDED_ARG already exists we need to increase it's argument
                        code_list[i - 1] += 1
                    else:
                        # if there isn't EXTENDED_ARG operator yet we have to insert the new operator
                        extended_arg_code = [EXTENDED_ARG, new_arg >> 8]
                        all_inserted_code.append(InsertedCode(i, extended_arg_code))
                    code_list[i + 1] = new_arg & MAX_BYTE

        code_list = code_list[:current_offset] + current_code_list + code_list[current_offset:]

        for k in range(len(all_inserted_code)):
            offset, inserted_code_list = all_inserted_code[k]
            if current_offset < offset:
                all_inserted_code[k] = InsertedCode(offset + len(current_code_list), inserted_code_list)
        j += 1

    return bytes(code_list), all_inserted_code


def add_jump_instruction(jump_arg, code_to_insert):
    """
    Note: although it's adding a POP_JUMP_IF_TRUE, it's actually no longer used now
    (we could only return the return and possibly the load of the 'None' before the
    return -- not done yet because it needs work to fix all related tests).
    """
    jump_arg = jump_arg // 2 if IS_PY310_OR_GREATER else jump_arg
    buffer = [opmap['POP_JUMP_IF_TRUE'], jump_arg & MAX_BYTE]

    jump_arg >>= 8
    numbers_of_extended_args = 0
    while jump_arg > 0:
        buffer += [EXTENDED_ARG, jump_arg & MAX_BYTE]
        jump_arg >>= 8
        numbers_of_extended_args += 1

    if numbers_of_extended_args > 3:
        raise RuntimeError("At most 3 prefixal EXTENDED_ARG are allowed, {} generated. "
                           "The passed `jump_arg` was {}"
                           .format(numbers_of_extended_args, jump_arg))

    op_list, arg_list = buffer[0::2], buffer[1::2]
    jump_instruction_list = []
    for op, arg in reversed(list(zip(op_list, arg_list))):
        jump_instruction_list.append(op)
        jump_instruction_list.append(arg)

    # Remove 'RETURN_VALUE' instruction and add 'POP_JUMP_IF_TRUE'
    # with (if needed) 'EXTENDED_ARG',
    return list(code_to_insert.co_code[:-RETURN_VALUE_SIZE]) + jump_instruction_list


_created = {}


def insert_code(code_to_modify, code_to_insert, before_line):
    # This check is needed for generator functions, because after each yield a new frame is created
    # but the former code object is used.

    ok_and_curr_before_line = _created.get(code_to_modify)
    if ok_and_curr_before_line is not None:
        ok, curr_before_line = ok_and_curr_before_line
        if not ok:
            return False, code_to_modify

        if curr_before_line == before_line:
            return True, code_to_modify

        return False, code_to_modify

    ok, new_code = _insert_code(code_to_modify, code_to_insert, before_line)
    _created[new_code] = ok, before_line
    return ok, new_code


def _insert_code(code_to_modify, code_to_insert, before_line):
    """
    Insert a piece of code `code_to_insert` to `code_to_modify` right inside the line
    `before_line` before the instruction on this line by modifying original bytecode

    :param code_to_modify: Code to modify
    :param code_to_insert: Code to insert
    :param before_line: Number of line for code insertion
    :return: boolean flag whether insertion was successful, modified code
    """
    line_starts = dict(dis.findlinestarts(code_to_modify))
    for off, line_no in line_starts.items():
        if line_no == before_line:
            offset = off
            break
    else:
        return False, code_to_modify

    code_to_insert_list = add_jump_instruction(offset, code_to_insert)
    try:
        code_to_insert_list, new_names = \
            _add_attr_values_from_insert_to_original(code_to_modify, code_to_insert, code_to_insert_list, 'co_names',
                                                     dis.hasname)
        code_to_insert_list, new_consts = \
            _add_attr_values_from_insert_to_original(code_to_modify, code_to_insert, code_to_insert_list, 'co_consts',
                                                     [opmap['LOAD_CONST']])
        code_to_insert_list, new_vars = \
            _add_attr_values_from_insert_to_original(code_to_modify, code_to_insert, code_to_insert_list, 'co_varnames',
                                                     dis.haslocal)
        new_bytes, all_inserted_code = _update_label_offsets(code_to_modify.co_code, offset, list(code_to_insert_list))

        new_lines = _modify_new_lines(code_to_modify, all_inserted_code)
        if new_lines is None:
            return False, code_to_modify

    except ValueError:
        traceback.print_exc()
        return False, code_to_modify

    args = [
        code_to_modify.co_argcount,  # integer
        code_to_modify.co_kwonlyargcount,  # integer
        len(new_vars),  # integer
        code_to_modify.co_stacksize,  # integer
        code_to_modify.co_flags,  # integer
        new_bytes,  # bytes
        new_consts,  # tuple
        new_names,  # tuple
        new_vars,  # tuple
        code_to_modify.co_filename,  # string
        code_to_modify.co_name,  # string
        code_to_modify.co_firstlineno,  # integer
        new_lines,  # bytes
        code_to_modify.co_freevars,  # tuple
        code_to_modify.co_cellvars  # tuple
    ]
    if sys.version_info >= (3, 8, 0):
        # Python 3.8 and above supports positional-only parameters. The number of such
        # parameters is passed to the constructor as the second argument.
        args.insert(1, code_to_modify.co_posonlyargcount)
    new_code = CodeType(*args)
    return True, new_code
