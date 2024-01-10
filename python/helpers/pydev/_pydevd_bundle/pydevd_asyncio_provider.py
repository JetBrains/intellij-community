#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
try:
    from pydevd_nest_asyncio import apply
except:
    apply = None

try:
    from pydevd_asyncio_utils import eval_async_expression_in_context
except:
    eval_async_expression_in_context = None

try:
    from pydevd_asyncio_utils import eval_async_expression
except:
    eval_async_expression = None

try:
    from pydevd_asyncio_utils import exec_async_code
except:
    exec_async_code = None

try:
    from pydevd_asyncio_utils import asyncio_command_compiler
except:
    asyncio_command_compiler = None
