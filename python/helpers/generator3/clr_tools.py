# coding=utf-8
"""
.NET (CLR) specific functions
"""
__author__ = 'Ilya.Kazakevich'


def get_namespace_by_name(object_name):
    """
    Gets namespace for full object name. Sometimes last element of name is module while it may be class.
    For System.Console returns System, for System.Web returns System.Web.
    Be sure all required assemblies are loaded (i.e. clr.AddRef.. is called)
    :param object_name: name to parse
    :return: namespace
    """
    (imported_object, object_name) = _import_first(object_name)
    parts = object_name.partition(".")
    first_part = parts[0]
    remain_part = parts[2]

    while remain_part and type(_get_attr_by_name(imported_object, remain_part)) is type:  # While we are in class
        remain_part = remain_part.rpartition(".")[0]

    if remain_part:
        return first_part + "." + remain_part
    else:
        return first_part


def _import_first(object_name):
    """
    Some times we can not import module directly. For example, Some.Class.InnerClass could not be imported: you need to import "Some.Class"
    or even "Some" instead. This function tries to find part of name that could be loaded

     :param object_name: name in dotted notation like "Some.Function.Here"
     :return: (imported_object, object_name): tuple with object and its name
    """
    while object_name:
        try:
            return (__import__(object_name, globals=[], locals=[], fromlist=[]), object_name)
        except ImportError:
            object_name = object_name.rpartition(".")[0]  # Remove rightest part
    raise Exception("No module name found in name " + object_name)


def _get_attr_by_name(obj, name):
    """
    Accepts chain of attributes in dot notation like "some.property.name" and gets them on object
    :param obj: object to introspec
    :param name: attribute name
    :return attribute

    >>> str(_get_attr_by_name("A", "__class__.__class__"))
    "<type 'type'>"

    >>> str(_get_attr_by_name("A", "__class__.__len__.__class__"))
    "<type 'method_descriptor'>"
    """
    result = obj
    parts = name.split('.')
    for part in parts:
        result = getattr(result, part)
    return result
