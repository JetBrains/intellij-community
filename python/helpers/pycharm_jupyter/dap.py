# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""Return viewer data as an ASCII string for DAP clipboard evaluation."""
import base64
from functools import wraps


def _encoded(command):
    @wraps(command)
    def execute(*args):
        try:
            success, result = command(*args)
        except Exception as error:
            success, result = False, error
        if isinstance(result, BaseException):
            result = "{}: {}".format(type(result).__name__, result)
        payload = base64.b64encode(str(result).encode("utf-8")).decode("ascii")
        return "PYCHARM_DATAVIEW_1:{}:{}".format(
            "OK" if success else "ERROR", payload,
        )
    return execute


@_encoded
def table(expression, command, start, end, format, f_globals, f_locals):
    from pycharm_tables.pydevd_tables import exec_table_command, TableCommandType
    if command not in (
        TableCommandType.DF_INFO, TableCommandType.SLICE,
        TableCommandType.SLICE_CSV, TableCommandType.DESCRIBE,
        TableCommandType.VISUALIZATION_DATA,
    ):
        raise ValueError("Unsupported table command: " + command)
    return exec_table_command(
        expression, command, start, end, format, f_globals, f_locals,
    )


@_encoded
def image(expression, command, offset, image_id, f_globals, f_locals):
    from pycharm_tables.pydevd_tables import (
        exec_image_table_command, TableCommandType,
    )
    if command == TableCommandType.IMAGE_START_CHUNK_LOAD:
        success, result = exec_image_table_command(
            expression, command, None, None, f_globals, f_locals,
        )
    elif command == TableCommandType.IMAGE_CHUNK_LOAD:
        from pycharm_tables.images.pydevd_image_loader import load_image_chunk
        if not isinstance(offset, int) or offset < 0:
            raise ValueError("Invalid image offset")
        success, result = True, load_image_chunk(offset, image_id)
    else:
        raise ValueError("Unsupported image command: " + command)
    if isinstance(result, str) and result.startswith("Error:"):
        success = False
    return success, result


@_encoded
def array(expression, row_offset, col_offset, rows, cols, format, f_globals, f_locals):
    from pycharm_jupyter.dap_array import table_like_struct_to_xml
    value = eval(expression, f_globals, f_locals)
    return True, table_like_struct_to_xml(
        value, expression, row_offset, col_offset, rows, cols, format,
    )


@_encoded
def metadata(expression, f_globals, f_locals):
    value = eval(expression, f_globals, f_locals)
    shape = getattr(value, "shape", None)
    dtype = getattr(value, "dtype", None)
    return True, "{}\t{}".format(
        "" if shape is None else str(tuple(shape)),
        "" if dtype is None else str(dtype),
    )
