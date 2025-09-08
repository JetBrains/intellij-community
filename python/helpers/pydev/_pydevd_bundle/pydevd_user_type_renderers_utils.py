#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import inspect
from _pydevd_bundle import pydevd_utils

try:
    # IDE's own module importer (handles namespace packages, zip, etc.)
    from _pydevd_bundle import pydevd_import_class as _pydevd_import_class
except Exception:
    _pydevd_import_class = None

def _resolve_type(path):
    """Resolve 'pkg.mod.Class' -> class object, or None."""
    if not path:
        return None
    # Prefer pydevd's own importer so future understandings
    # over runtime module imports may propagate seamlessly
    if _pydevd_import_class is not None:
        for attr in ('import_name', 'ImportName'):
            fn = getattr(_pydevd_import_class, attr, None)
            if callable(fn):
                try:
                    return fn(path)
                except:
                    pass
    return None

def _by_type_entities(cls, renderers_dict):
    """Resolve names into types for entity-bound type matching criteria"""
    for render in [render for renders in renderers_dict.values() for render in renders]:
        try:
            for name_type in ('type_canonical_import_path','type_qualified_name'):
                target = _resolve_type(getattr(render, name_type, None))
                if cls is target:
                    return render
        except:
            pass
    return None


class TypeRenderersConstants:
    new_line = "@_@NEW_LINE_CHAR@_@"
    tab = "@_@TAB_CHAR@_@"


def try_get_type_renderer_for_var(var, renderers_dict):
    try:
        cls = var.__class__
        cls_name = cls.__name__
        renderers_for_name = renderers_dict.get(cls_name)
        if renderers_for_name is None:
            # by class type entities
            return _by_type_entities(cls, renderers_dict)

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
            # by class type entities
            return _by_type_entities(cls, renderers_dict)

        # by module root and class name
        # (if module contains only one class with the same name)
        module_root = module_name.split(".")[0]
        for render in renderers_for_name:
            if render.module_root_has_one_type_with_same_name:
                renderer_module_root = render.type_canonical_import_path.split(".")[0]
                if renderer_module_root == module_root:
                    return render

        # by qualified name
        return _by_type_entities(cls, renderers_dict)

    except:
        pass

    return None
