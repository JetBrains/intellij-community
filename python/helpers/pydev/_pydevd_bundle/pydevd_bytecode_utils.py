"""Bytecode analyzing utils."""

from _pydevd_bundle.smart_step_into import get_stepping_variants
from _pydevd_bundle.pydevd_constants import IS_PY311_OR_GREATER, IS_PY39_OR_GREATER
import dis


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

    def process_instructions(order, start_offset=None, end_offset=None, find_reraise=False):
        """Processes instructions within the given bytecode offsets."""
        stepping_generator = get_stepping_variants(code)
        for inst in stepping_generator:
            if end_offset is not None and inst.offset > end_offset:
                if not find_reraise:
                    break
                else:
                    for forward in get_stepping_variants(code):
                        if forward.lineno > frame.f_lineno:
                            break
                        cache_for_reraise = cache.copy()
                        name = forward.argval
                        call_order_reraise = cache_for_reraise.setdefault(name, -1)
                        call_order_reraise += 1
                        cache_for_reraise[name] = call_order_reraise
                        if name == "RERAISE":
                            order = call_order_reraise
                            break
                    break

            if start_offset is not None and inst.offset < start_offset:
                continue
            if start_line <= inst.lineno <= frame.f_lineno:
                name = inst.argval
                order = cache.setdefault(name, -1)
                order += 1
                cache[name] = order
        return order

    if IS_PY311_OR_GREATER:
        exception_table = dis._parse_exception_table(code)
        if exception_table:
            for i, entry in enumerate(exception_table):
                if entry.start < lasti < entry.end:
                    target_entry = None
                    for next_entry in exception_table[i + 1:]:
                        if next_entry.start == entry.target:
                            target_entry = next_entry
                            break
                    if target_entry is not None:
                        call_order = process_instructions(call_order, target_entry.start, target_entry.end)
                        break
            else:
                call_order = process_instructions(call_order, None, lasti)
        else:
            call_order = process_instructions(call_order, None, lasti)
    elif IS_PY39_OR_GREATER:
        call_order = process_instructions(call_order, None, lasti, find_reraise=True)
    else:
        call_order = process_instructions(call_order, None, lasti)

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
