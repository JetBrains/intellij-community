"""Bytecode manipulation for coverage.py"""

import opcode, types

from coverage.backward import byte_to_int

class ByteCode(object):
    """A single bytecode."""
    def __init__(self):
        # The offset of this bytecode in the code object.
        self.offset = -1

        # The opcode, defined in the `opcode` module.
        self.op = -1

        # The argument, a small integer, whose meaning depends on the opcode.
        self.arg = -1

        # The offset in the code object of the next bytecode.
        self.next_offset = -1

        # The offset to jump to.
        self.jump_to = -1


class ByteCodes(object):
    """Iterator over byte codes in `code`.

    Returns `ByteCode` objects.

    """
    # pylint: disable=R0924
    def __init__(self, code):
        self.code = code

    def __getitem__(self, i):
        return byte_to_int(self.code[i])

    def __iter__(self):
        offset = 0
        while offset < len(self.code):
            bc = ByteCode()
            bc.op = self[offset]
            bc.offset = offset

            next_offset = offset+1
            if bc.op >= opcode.HAVE_ARGUMENT:
                bc.arg = self[offset+1] + 256*self[offset+2]
                next_offset += 2

                label = -1
                if bc.op in opcode.hasjrel:
                    label = next_offset + bc.arg
                elif bc.op in opcode.hasjabs:
                    label = bc.arg
                bc.jump_to = label

            bc.next_offset = offset = next_offset
            yield bc


class CodeObjects(object):
    """Iterate over all the code objects in `code`."""
    def __init__(self, code):
        self.stack = [code]

    def __iter__(self):
        while self.stack:
            # We're going to return the code object on the stack, but first
            # push its children for later returning.
            code = self.stack.pop()
            for c in code.co_consts:
                if isinstance(c, types.CodeType):
                    self.stack.append(c)
            yield code
