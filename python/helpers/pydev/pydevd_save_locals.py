"""
Utility for saving locals.
"""
import sys
import pydevd_vars

def is_save_locals_available():
    try:
        if '__pypy__' in sys.builtin_module_names:
            import __pypy__
            save_locals = __pypy__.locals_to_fast
            return True
    except:
        pass


    try:
        import ctypes
    except:
        return False #Not all Python versions have it

    try:
        func = ctypes.pythonapi.PyFrame_LocalsToFast
    except:
        return False

    return True

def save_locals(frame):
    """
    Copy values from locals_dict into the fast stack slots in the given frame.

    Note: the 'save_locals' branch had a different approach wrapping the frame (much more code, but it gives ideas
    on how to save things partially, not the 'whole' locals).
    """
    if not isinstance(frame, pydevd_vars.frame_type):
        # Fix exception when changing Django variable (receiving DjangoTemplateFrame)
        return

    try:
        if '__pypy__' in sys.builtin_module_names:
            import __pypy__
            save_locals = __pypy__.locals_to_fast
            save_locals(frame)
            return
    except:
        pass


    try:
        import ctypes
    except:
        return #Not all Python versions have it

    try:
        func = ctypes.pythonapi.PyFrame_LocalsToFast
    except:
        return

    #parameter 0: don't set to null things that are not in the frame.f_locals (which seems good in the debugger context).
    func(ctypes.py_object(frame), ctypes.c_int(0))


