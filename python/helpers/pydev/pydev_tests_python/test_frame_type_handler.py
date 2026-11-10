import re

import pytest

from _pydevd_bundle.pydevd_constants import GET_FRAME_SPECIAL_GROUP

FRAME_LOCALS = {
    '__name__': '__main__',
    '__doc__': None,
    '__builtins__': {'ord': ord},
    'x': 1,
}
SPECIAL_NAMES = ['__builtins__', '__doc__', '__name__']
UNUSED_TYPE_RENDERERS = {'test.UnusedType': object()}


@pytest.mark.parametrize('user_type_renderers', [{}, UNUSED_TYPE_RENDERERS])
def test_special_group_struct(user_type_renderers, capsys):
    from _pydevd_bundle.pydevd_thrift import frame_vars_to_struct

    values = frame_vars_to_struct(FRAME_LOCALS, GET_FRAME_SPECIAL_GROUP, user_type_renderers=user_type_renderers)

    assert [value.name for value in values] == SPECIAL_NAMES
    assert 'Traceback' not in capsys.readouterr().err


@pytest.mark.parametrize('user_type_renderers', [None, UNUSED_TYPE_RENDERERS])
def test_special_group_xml(user_type_renderers, capsys):
    from _pydevd_bundle.pydevd_xml import frame_vars_to_xml

    xml = frame_vars_to_xml(FRAME_LOCALS, GET_FRAME_SPECIAL_GROUP, user_type_renderers=user_type_renderers)

    assert re.findall(r'<var name="([^"]+)"', xml) == SPECIAL_NAMES
    assert 'Traceback' not in capsys.readouterr().err
