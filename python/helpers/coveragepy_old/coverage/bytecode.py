# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Bytecode manipulation for coverage.py"""

import types


def code_objects(code):
    """Iterate over all the code objects in `code`."""
    stack = [code]
    while stack:
        # We're going to return the code object on the stack, but first
        # push its children for later returning.
        code = stack.pop()
        for c in code.co_consts:
            if isinstance(c, types.CodeType):
                stack.append(c)
        yield code
