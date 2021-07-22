#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import inspect

from _pydevd_bundle import pydevd_utils


class TypeRenderersConstants:
    temp_var_prefix = "__py_debug_user_type_renderers_temp_var_"
    left_paren_char = "@_@LEFT_PAREN_CHAR@_@"
    right_paren_char = "@_@RIGHT_PAREN_CHAR@_@"
    new_line = "@_@NEW_LINE_CHAR@_@"
    tab = "@_@TAB_CHAR@_@"


def try_get_type_renderer_for_var(var, renderers_dict):
    try:
        cls = var.__class__
        cls_name = cls.__name__
        renderers_for_name = renderers_dict.get(cls_name)
        if renderers_for_name is None:
            return None

        module_name = cls.__module__
        qualified_name = module_name + "." + cls_name

        # for builtins
        builtin_module = int.__module__
        if module_name == builtin_module:
            for render in renderers_for_name:
                if render.type_canonical_import_path == qualified_name:
                    return render

        # for classes which defined in project directory
        try:
            src_file = inspect.getfile(cls)
        except:
            src_file = None

        if src_file is not None and pydevd_utils.in_project_roots(src_file):
            for render in renderers_for_name:
                if render.type_src_file == src_file:
                    return render
            return None

        # by qualified name
        for render in renderers_for_name:
            if render.type_qualified_name == qualified_name:
                return render

        # by module root and class name
        # (if module contains only one class with the same name)
        module_root = module_name.split(".")[0]
        for render in renderers_for_name:
            if render.module_root_has_one_type_with_same_name:
                renderer_module_root = render.type_canonical_import_path.split(".")[0]
                if renderer_module_root == module_root:
                    return render
    except:
        pass

    return None


def try_get_custom_var_string_repr(var_obj, user_type_renderers):
    type_renderer = try_get_type_renderer_for_var(var_obj, user_type_renderers)
    if type_renderer is not None:
        return type_renderer.evaluate_var_string_repr(var_obj)

    return None


def is_temp_var(var_name):
    return var_name.startswith(TypeRenderersConstants.temp_var_prefix)


def create_temp_var(expression):
    expression = expression \
        .replace("(", TypeRenderersConstants.left_paren_char) \
        .replace(")", TypeRenderersConstants.right_paren_char)
    return TypeRenderersConstants.temp_var_prefix + expression


def get_expression_from_temp_var(var_name):
    if var_name.startswith(TypeRenderersConstants.temp_var_prefix):
        var_name = var_name[len(TypeRenderersConstants.temp_var_prefix):]
        return var_name \
            .replace(TypeRenderersConstants.left_paren_char, "(") \
            .replace(TypeRenderersConstants.right_paren_char, ")")

    return var_name
