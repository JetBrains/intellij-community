class CPythonTrapClass(object):
    pass

should_fall_into_trap = None

def get_fall_into_trap():
    return should_fall_into_trap

def set_fall_into_trap(thread_id):
    global should_fall_into_trap
    should_fall_into_trap = thread_id


def cpython_trap():
    """Native debugger trap for CPython."""
    # invokes type_set_bases() in Objects/typeobject.c
    CPythonTrapClass.__bases__ = CPythonTrapClass.__bases__