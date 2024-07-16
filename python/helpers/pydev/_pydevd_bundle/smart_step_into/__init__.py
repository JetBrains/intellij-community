#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""Bytecode analyzing utils for extracting smart steps into variants."""

from collections import namedtuple

from _pydevd_bundle.pydevd_constants import IS_PY311_OR_GREATER, IS_PY36_OR_GREATER, \
    IS_PY2, IS_CPYTHON

__all__ = [
    'find_stepping_variants',
    'get_stepping_variants',
]


def _get_stepping_variants_dummy(_):
    return []


get_stepping_variants = _get_stepping_variants_dummy

if not IS_CPYTHON:
    pass
elif IS_PY311_OR_GREATER:
    from _pydevd_bundle.smart_step_into.pydevd_smart_step_into_311 import (
        get_stepping_variants)
elif IS_PY36_OR_GREATER:
    from _pydevd_bundle.smart_step_into.pydevd_smart_step_into_36_310 import (
        get_stepping_variants)
elif IS_PY2:
    from _pydevd_bundle.smart_step_into.pydevd_smart_step_into_27 import (
        get_stepping_variants)


_Variant = namedtuple('Variant', ['name', 'is_visited'])


def find_stepping_variants(frame, start_line, end_line):
    """Finds the *ordered* stepping targets within the given line range."""
    code = frame.f_code
    last_instruction = frame.f_lasti
    for instruction in get_stepping_variants(code):
        if not instruction.lineno:
            continue
        if start_line <= instruction.lineno <= end_line:
            yield _Variant(instruction.argval, instruction.offset <= last_instruction)
        if instruction.lineno > end_line:
            break
