from typing import Any

chardet_type: Any

def chardet_dammit(s): ...

xml_encoding: str
html_meta: str
encoding_res: Any

class EntitySubstitution:
    CHARACTER_TO_HTML_ENTITY: Any
    HTML_ENTITY_TO_CHARACTER: Any
    CHARACTER_TO_HTML_ENTITY_RE: Any
    CHARACTER_TO_XML_ENTITY: Any
    BARE_AMPERSAND_OR_BRACKET: Any
    AMPERSAND_OR_BRACKET: Any
    @classmethod
    def quoted_attribute_value(cls, value): ...
    @classmethod
    def substitute_xml(cls, value, make_quoted_attribute: bool = ...): ...
    @classmethod
    def substitute_xml_containing_entities(cls, value, make_quoted_attribute: bool = ...): ...
    @classmethod
    def substitute_html(cls, s): ...

class EncodingDetector:
    override_encodings: Any
    exclude_encodings: Any
    chardet_encoding: Any
    is_html: Any
    declared_encoding: Any
    def __init__(
        self, markup, override_encodings: Any | None = ..., is_html: bool = ..., exclude_encodings: Any | None = ...
    ) -> None: ...
    @property
    def encodings(self) -> None: ...
    @classmethod
    def strip_byte_order_mark(cls, data): ...
    @classmethod
    def find_declared_encoding(cls, markup, is_html: bool = ..., search_entire_document: bool = ...): ...

class UnicodeDammit:
    CHARSET_ALIASES: Any
    ENCODINGS_WITH_SMART_QUOTES: Any
    smart_quotes_to: Any
    tried_encodings: Any
    contains_replacement_characters: bool
    is_html: Any
    log: Any
    detector: Any
    markup: Any
    unicode_markup: Any
    original_encoding: Any
    def __init__(
        self, markup, override_encodings=..., smart_quotes_to: Any | None = ..., is_html: bool = ..., exclude_encodings=...
    ) -> None: ...
    @property
    def declared_html_encoding(self): ...
    def find_codec(self, charset): ...
    MS_CHARS: Any
    MS_CHARS_TO_ASCII: Any
    WINDOWS_1252_TO_UTF8: Any
    MULTIBYTE_MARKERS_AND_SIZES: Any
    FIRST_MULTIBYTE_MARKER: Any
    LAST_MULTIBYTE_MARKER: Any
    @classmethod
    def detwingle(cls, in_bytes, main_encoding: str = ..., embedded_encoding: str = ...): ...
