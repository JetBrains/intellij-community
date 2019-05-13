import dis
import traceback
from opcode import opmap, EXTENDED_ARG, HAVE_ARGUMENT
from types import CodeType

MAX_BYTE = 255
RETURN_VALUE_SIZE = 2


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
    """
    Update new lines in order to hide inserted code inside the original code
    :param code_to_modify: code to modify
    :param all_inserted_code: list of tuples (offset, list of code instructions) with all inserted pieces of code
    :return: bytes sequence of code with updated lines offsets
    """
    new_list = list(code_to_modify.co_lnotab)
    abs_offset = prev_abs_offset = 0
    i = 0
    while i < len(new_list):
        prev_abs_offset = abs_offset
        abs_offset += new_list[i]
        for (inserted_offset, inserted_code) in all_inserted_code:
            if prev_abs_offset <= inserted_offset < abs_offset:
                size_of_inserted = len(inserted_code)
                new_list[i] += size_of_inserted
                abs_offset += size_of_inserted
        if new_list[i] > MAX_BYTE:
            new_list[i] = new_list[i] - MAX_BYTE
            new_list = new_list[:i] + [MAX_BYTE, 0] + new_list[i:]
        i += 2
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
            if not extended_arg:
                # in case if we added EXTENDED_ARG, but haven't inserted it to the source code yet.
                for code_index in range(current_index, len(inserted_code_list)):
                    inserted_offset, inserted_code = inserted_code_list[code_index]
                    if inserted_offset == i and inserted_code[0] == EXTENDED_ARG:
                        extended_arg = inserted_code[1] << 8
            arg = code[i+1] | extended_arg
            extended_arg = (arg << 8) if op == EXTENDED_ARG else 0
        else:
            arg = None
        yield (i, op, arg)


def _update_label_offsets(code_obj, breakpoint_offset, breakpoint_code_list):
    """
    Update labels for the relative and absolute jump targets
    :param code_obj: code to modify
    :param breakpoint_offset: offset for the inserted code
    :param breakpoint_code_list: size of the inserted code
    :return: bytes sequence with modified labels; list of tuples (resulting offset, list of code instructions) with
    information about all inserted pieces of code
    """
    inserted_code = list()
    # the list with all inserted pieces of code
    inserted_code.append((breakpoint_offset, breakpoint_code_list))
    code_list = list(code_obj)
    j = 0

    while j < len(inserted_code):
        current_offset, current_code_list = inserted_code[j]
        offsets_for_modification = []

        for offset, op, arg in _unpack_opargs(code_list, inserted_code, j):
            if arg is not None:
                if op in dis.hasjrel:
                    # has relative jump target
                    label = offset + 2 + arg
                    if offset < current_offset < label:
                        # change labels for relative jump targets if code was inserted inside
                        offsets_for_modification.append(offset)
                elif op in dis.hasjabs:
                    # change label for absolute jump if code was inserted before it
                    if current_offset < arg:
                        offsets_for_modification.append(offset)
        for i in range(0, len(code_list), 2):
            op = code_list[i]
            if i in offsets_for_modification and op >= dis.HAVE_ARGUMENT:
                new_arg = code_list[i + 1] + len(current_code_list)
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
                        inserted_code.append((i, extended_arg_code))
                    code_list[i + 1] = new_arg & MAX_BYTE

        code_list = code_list[:current_offset] + current_code_list + code_list[current_offset:]

        for k in range(len(inserted_code)):
            offset, inserted_code_list = inserted_code[k]
            if current_offset < offset:
                inserted_code[k] = (offset + len(current_code_list), inserted_code_list)
        j += 1

    return bytes(code_list), inserted_code


def _return_none_fun():
    return None


def add_jump_instruction(jump_arg, code_to_insert):
    """
    Add additional instruction POP_JUMP_IF_TRUE to implement a proper jump for "set next statement" action
    Jump should be done to the beginning of the inserted fragment
    :param jump_arg: argument for jump instruction
    :param code_to_insert: code to insert
    :return: a code to insert with properly added jump instruction
    """
    extended_arg_list = []
    if jump_arg > MAX_BYTE:
        extended_arg_list += [EXTENDED_ARG, jump_arg >> 8]
        jump_arg = jump_arg & MAX_BYTE

    # remove 'RETURN_VALUE' instruction and add 'POP_JUMP_IF_TRUE' with (if needed) 'EXTENDED_ARG'
    return list(code_to_insert.co_code[:-RETURN_VALUE_SIZE]) + extended_arg_list + [opmap['POP_JUMP_IF_TRUE'], jump_arg]


def insert_code(code_to_modify, code_to_insert, before_line):
    """
    Insert piece of code `code_to_insert` to `code_to_modify` right inside the line `before_line` before the
    instruction on this line by modifying original bytecode

    :param code_to_modify: Code to modify
    :param code_to_insert: Code to insert
    :param before_line: Number of line for code insertion
    :return: boolean flag whether insertion was successful, modified code
    """
    linestarts = dict(dis.findlinestarts(code_to_modify))
    if before_line not in linestarts.values():
        return code_to_modify
    offset = None
    for off, line_no in linestarts.items():
        if line_no == before_line:
            offset = off

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

        new_lnotab = _modify_new_lines(code_to_modify, all_inserted_code)
    except ValueError:
        traceback.print_exc()
        return False, code_to_modify

    new_code = CodeType(
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
        new_lnotab,  # bytes
        code_to_modify.co_freevars,  # tuple
        code_to_modify.co_cellvars  # tuple
    )
    return True, new_code
