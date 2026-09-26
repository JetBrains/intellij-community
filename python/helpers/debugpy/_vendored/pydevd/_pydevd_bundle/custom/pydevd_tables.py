#  Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import os
import sys
from contextlib import contextmanager


@contextmanager
def temp_sys_path(path: str):
    needs_insertion = path not in sys.path
    if needs_insertion:
        sys.path.insert(0, path)
    try:
        yield
    finally:
        if needs_insertion:
            sys.path.remove(path)


def _safe_import_tables():
    try:
        from pycharm_tables.pydevd_tables import (
            TableCommandType,
            exec_image_table_command,
            exec_table_command,
        )
        return TableCommandType, exec_image_table_command, exec_table_command
    except:
        return None, None, None


cur_file_path = os.path.dirname(os.path.abspath(__file__))
helpers_root = os.path.abspath(os.path.join(cur_file_path, "..", "..", "..", "..", ".."))

with temp_sys_path(helpers_root):
    TableCommandType, exec_image_table_command, exec_table_command = _safe_import_tables()
