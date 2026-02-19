#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_user_type_renderers_utils import TypeRenderersConstants
from _pydevd_bundle.pydevd_xml import ExceptionOnEvaluate

try:
    from collections import OrderedDict
except:
    OrderedDict = dict


class UserTypeRenderer(object):
    def __init__(
            self,
            to_type,
            type_canonical_import_path,
            type_qualified_name,
            type_src_file,
            module_root_has_one_type_with_same_name,
            is_default_value,
            expression,
            is_default_children,
            append_default_children,
            children
    ):
        self.to_type = to_type
        self.type_canonical_import_path = type_canonical_import_path
        self.type_qualified_name = type_qualified_name
        self.type_src_file = type_src_file
        self.module_root_has_one_type_with_same_name = \
            module_root_has_one_type_with_same_name
        self.is_default_value = is_default_value
        self.expression = _convert_empty_expression(expression)
        self.is_default_children = is_default_children
        self.append_default_children = append_default_children
        self.children = [TypeChildInfo(_convert_empty_expression(child.expression))
                         for child in children]

    def evaluate_var_string_repr(self, var_obj):
        if self.is_default_value:
            return None

        context = {"self": var_obj}
        eval_result = pydevd_vars.eval_in_context(self.expression, context, context)
        return _obj_to_string(eval_result)


class TypeChildInfo(object):
    def __init__(self, expression):
        self.expression = expression


def _convert_empty_expression(expression):
    if expression == "":
        return "\"\""
    return expression


def _obj_to_string(obj):
    error_prefix = ""
    try:
        is_exception_on_eval = obj.__class__ == ExceptionOnEvaluate
    except:
        is_exception_on_eval = False
    if is_exception_on_eval:
        obj = obj.result
        error_prefix = "Unable to evaluate: "
    try:
        return error_prefix + str(obj)
    except:
        try:
            return error_prefix + repr(obj)
        except:
            return 'Unable to get repr for %s' % obj.__class__


def _convert_expression(expr):
    return expr \
        .replace(TypeRenderersConstants.new_line, '\n') \
        .replace(TypeRenderersConstants.tab, '\t') \
        .strip()


def parse_set_type_renderers_message(message_text):
    sequence = message_text.split('\t')[1:]
    type_renderers = {}
    current_index = 0

    while current_index < len(sequence):
        message_size = int(sequence[current_index])
        current_index += 1

        message = sequence[current_index:current_index + message_size]
        to_type = _convert_expression(message[0])
        type_canonical_import_path = _convert_expression(message[1])
        type_qualified_name = _convert_expression(message[2])
        type_src_file = _convert_expression(message[3])
        module_root_has_one_type_with_same_name = bool(int(message[4]))
        is_default_value = bool(int(message[5]))
        expression = _convert_expression(message[6])
        is_default_children = bool(int(message[7]))
        append_default_children = bool(int(message[8]))

        message_tail = message[9:]
        children = [TypeChildInfo(_convert_expression(expr))
                    for expr in message_tail]

        renderer = UserTypeRenderer(
            to_type,
            type_canonical_import_path,
            type_qualified_name,
            type_src_file,
            module_root_has_one_type_with_same_name,
            is_default_value,
            expression,
            is_default_children,
            append_default_children,
            children
        )

        type_components = type_canonical_import_path.split('.')
        if len(type_components) >= 2:
            type_name = type_components[-1]
            if type_name not in type_renderers:
                type_renderers[type_name] = []
            type_renderers[type_name].append(renderer)

        current_index += message_size

    return type_renderers
