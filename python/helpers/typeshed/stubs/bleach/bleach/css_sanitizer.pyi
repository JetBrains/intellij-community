from collections.abc import Container

ALLOWED_CSS_PROPERTIES: frozenset[str]
ALLOWED_SVG_PROPERTIES: frozenset[str]

class CSSSanitizer:
    allowed_css_properties: Container[str]
    allowed_svg_properties: Container[str]

    def __init__(self, allowed_css_properties: Container[str] = ..., allowed_svg_properties: Container[str] = ...) -> None: ...
    def sanitize_css(self, style: str) -> str: ...
