"""Bytecode analyzing utils."""

from _pydevd_bundle.smart_step_into import get_stepping_variants

__all__ = [
    'find_last_call_name',
    'find_last_func_call_order',
]


def find_last_func_call_order(frame, start_line):
    """Find the call order of the last function call between ``start_line``
    and last executed instruction.

    :param frame: A frame inside which we are looking for the function call.
    :type frame: :py:class:`types.FrameType`
    :param start_line:
    :return: call order or -1 if we fail to find the call order for some
      reason.
    :rtype: int
    """
    code = frame.f_code
    lasti = frame.f_lasti
    cache = {}
    call_order = -1
    for inst in get_stepping_variants(code):
        if inst.offset > lasti:
            break
        if inst.lineno >= start_line:
            name = inst.argval
            call_order = cache.setdefault(name, -1)
            call_order += 1
            cache[name] = call_order
    return call_order


def find_last_call_name(frame):
    """Find the name of the last call made in the frame.

    :param frame: A frame inside which we are looking the last call.
    :type frame: :py:class:`types.FrameType`
    :return: The name of a function or method that has been called last.
    :rtype: str
    """
    last_call_name = None
    code = frame.f_code
    last_instruction = frame.f_lasti
    for inst in get_stepping_variants(code):
        if inst.offset > last_instruction:
            break
        last_call_name = inst.argval

    return last_call_name
