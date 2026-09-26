# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""Use the table serializer without changing the shared module or debugpy."""
from importlib.util import module_from_spec, spec_from_file_location

from _pydev_bundle.pydev_imports import quote
from _pydevd_bundle.pydevd_xml import get_type, make_valid_xml_value
from pycharm_tables import pydevd_tabular_to_xml


def _cell_to_xml(value, name, format):
    try:
        formatted_value = format % value
    except (TypeError, ValueError):
        formatted_value = str(value)
    return '<var name="" type="%s" value="%s" />\n' % (
        make_valid_xml_value(get_type(value)[1]), quote(formatted_value),
    )


def _load_serializer():
    spec = spec_from_file_location(
        __name__ + "._serializer", pydevd_tabular_to_xml.__file__,
    )
    serializer = module_from_spec(spec)
    spec.loader.exec_module(serializer)
    serializer.var_to_xml = _cell_to_xml
    return serializer.legacy_table_like_struct_to_xml


table_like_struct_to_xml = _load_serializer()
