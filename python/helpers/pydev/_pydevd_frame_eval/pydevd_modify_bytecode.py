import dis
from types import CodeType
from opcode import opmap


def _modify_code_arguments(original_code, insert_code, insert_code_obj, attr, op_list):
    """
    Modify arguments for some code attributes. This function append values of the inserted code to the original values,
     and changes indexes inside inserted code. So it helps to avoid variables mixing between two pieces of code

    :param original_code: code to modify
    :param insert_code: code to insert
    :param insert_code_obj: bytes sequence of inserted code, which should be modified too
    :param attr: name of attribute ('co_names', 'co_consts' or 'co_varnames')
    :param op_list: sequence of bytecodes whose arguments should be changed
    :return:
    """
    orig_value = getattr(original_code, attr)
    insert_value = getattr(insert_code, attr)
    orig_names_len = len(orig_value)
    code_with_new_values = list(insert_code_obj)
    for offset, op, arg in dis._unpack_opargs(insert_code_obj):
        if op in op_list:
            code_with_new_values[offset + 1] += orig_names_len
    new_values = orig_value + insert_value
    return bytes(code_with_new_values), new_values


def _modify_new_lines(code_to_modify, code_insert, before_line_offset):
    new_list = list(code_to_modify.co_lnotab)
    abs_offset = 0
    for i in range(0, len(new_list), 2):
        abs_offset += new_list[i]
        if abs_offset == before_line_offset and (i + 2) < len(new_list):
            new_list[i + 2] += len(code_insert)
    return bytes(new_list)


def _modify_labels(code_obj, offset_of_inserted_code, size_of_inserted_code):
    """
    Update labels for the relative jump targets
    :param code_obj: code to modify
    :param offset_of_inserted_code: offset for the inserted code
    :param offset_of_inserted_code: size of the inserted code
    :return: bytes sequence with modified labels
    """
    offsets_for_modification = []
    for offset, op, arg in dis._unpack_opargs(code_obj):
        if arg is not None:
            if op in dis.hasjrel:
                label = offset + 2 + arg
                if offset < offset_of_inserted_code < label:
                    # change labels for relative jump targets if code was inserted inside
                    offsets_for_modification.append(offset)
            elif op in dis.hasjabs:
                # change label for absolute jump if code was inserted before it
                if offset_of_inserted_code <= arg:
                    offsets_for_modification.append(offset)
    code_list = list(code_obj)
    for i in range(0, len(code_obj), 2):
        op = code_list[i]
        if i in offsets_for_modification and op >= dis.HAVE_ARGUMENT:
            code_list[i + 1] += size_of_inserted_code
    return bytes(code_list)


def _return_none_fun():
    return None


def insert_code(code_to_modify, code_to_insert, before_line):
    """
    Insert piece of code `code_to_insert` to `code_to_modify` right inside the line `before_line` before the
    instruction on this line by modifying original bytecode

    :param code_to_modify: Code to modify
    :param code_to_insert: Code to insert
    :param before_line: Number of line for code insertion
    :return: modified code
    """
    linestarts = dict(dis.findlinestarts(code_to_modify))
    if before_line not in linestarts.values():
        return code_to_modify
    offset = None
    for off, line_no in linestarts.items():
        if line_no == before_line:
            offset = off

    return_none_size = len(_return_none_fun.__code__.co_code)
    code_to_insert_obj = code_to_insert.co_code[:-return_none_size]
    code_to_insert_obj, new_names = _modify_code_arguments(code_to_modify, code_to_insert, code_to_insert_obj,
                                                           'co_names', dis.hasname)
    code_to_insert_obj, new_consts = _modify_code_arguments(code_to_modify, code_to_insert, code_to_insert_obj,
                                                            'co_consts', [opmap['LOAD_CONST']])
    code_to_insert_obj, new_vars = _modify_code_arguments(code_to_modify, code_to_insert, code_to_insert_obj,
                                                          'co_varnames', dis.haslocal)
    modified_code = _modify_labels(code_to_modify.co_code, offset, len(code_to_insert_obj))
    new_bytes = modified_code[:offset] + code_to_insert_obj + modified_code[offset:]

    new_lnotab = _modify_new_lines(code_to_modify, code_to_insert_obj, offset)

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
    return new_code
