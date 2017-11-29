# coding=utf-8
"""
Internal package tools shared between argparse and optparse
"""
__author__ = 'Ilya.Kazakevich'


def get_opt_type(opt):
    """
    If option accepts arg, we need to determine its type. It could be int, choices, or something other.
    Accepts option (from arg or opt) and returns its type (as scalar or list in case of choices).
    Arg should have "type" and "choices" field.

    :param opt option or action from argparse or optparse that has type and choices field
    :return: type
    """
    if opt.type in ["int", "long"]:
        return "int"
    elif opt.choices:
        assert isinstance(opt.choices,  (list, tuple)), "Choices should be list or tuple"
        return opt.choices
    return "str"
