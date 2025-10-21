#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
def get_apply():
    try:
        from pydevd_nest_asyncio import apply
        return apply
    except:
        return None


def get_eval_async_expression_in_context():
    try:
        from pydevd_asyncio_utils import eval_async_expression_in_context
        return eval_async_expression_in_context
    except:
        return None


def get_eval_async_expression():
    try:
        from pydevd_asyncio_utils import eval_async_expression
        return eval_async_expression
    except:
        return None


def get_exec_async_code():
    try:
        from pydevd_asyncio_utils import exec_async_code
        return exec_async_code
    except:
        return None


def get_asyncio_command_compiler():
    try:
        from pydevd_asyncio_utils import asyncio_command_compiler
        return asyncio_command_compiler
    except:
        return None
