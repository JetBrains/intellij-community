from typing_extensions import assert_type

from pygments.style import Style, _StyleDict
from pygments.token import _TokenType


def test_style_class_iterable(style_class: type[Style]) -> None:
    for t, d in style_class:
        assert_type(t, _TokenType)
        assert_type(d, _StyleDict)
