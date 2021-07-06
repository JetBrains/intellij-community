#  Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR
from _pydevd_bundle.pydevd_xml import ExceptionOnEvaluate

import sys

MAX_COLS = 500
MAX_COLWIDTH = 200


class TableCommandType:
    DF_INFO = "DF_INFO"
    SLICE = "SLICE"


def is_error_on_eval(val):
    try:
        # This should be faster than isinstance (but we have to protect against not
        # having a '__class__' attribute).
        is_exception_on_eval = val.__class__ == ExceptionOnEvaluate
    except:
        is_exception_on_eval = False
    return is_exception_on_eval


def exec_table_command(init_command, command_type, f_globals, f_locals):
    # noinspection PyUnresolvedReferences
    res = ""
    if command_type == TableCommandType.DF_INFO:
        if 'pd' not in sys.modules:
            exec('import pandas as pd', f_globals, f_locals)
        tmp_var = pydevd_vars.eval_in_context(init_command, f_globals, f_locals)
        is_exception_on_eval = is_error_on_eval(tmp_var)
        if is_exception_on_eval:
            return False, tmp_var.result
        res += str(type(tmp_var))
        res += NEXT_VALUE_SEPARATOR
        res += str(tmp_var.shape[0])
        res += NEXT_VALUE_SEPARATOR
        res += repr(tmp_var.head().to_html(notebook=True,
                                           max_cols=MAX_COLS))
    elif command_type == TableCommandType.SLICE:
        import pandas as pd
        _jb_max_cols = pd.get_option('display.max_columns')
        _jb_max_colwidth = pd.get_option('display.max_colwidth')
        pd.set_option('display.max_colwidth', MAX_COLWIDTH)
        tmp_var = pydevd_vars.eval_in_context(init_command, f_globals, f_locals)
        is_exception_on_eval = is_error_on_eval(tmp_var)
        if is_exception_on_eval:
            return False, tmp_var.result
        res += repr(tmp_var.to_html(notebook=True,
                                    max_cols=MAX_COLS))
        pd.set_option('display.max_colwidth', _jb_max_colwidth)
    return True, res
