from _typeshed import Self
from typing import Any, BinaryIO, Callable, ClassVar, Mapping, Sequence, Text, TextIO
from typing_extensions import Literal
from xml.etree.ElementTree import Element

from .blockparser import BlockParser
from .extensions import Extension
from .util import HtmlStash, Registry

class Markdown:
    preprocessors: Registry
    inlinePatterns: Registry
    treeprocessors: Registry
    postprocessors: Registry
    parser: BlockParser
    htmlStash: HtmlStash
    output_formats: ClassVar[dict[Literal["xhtml", "html"], Callable[[Element], Text]]]
    output_format: Literal["xhtml", "html"]
    serializer: Callable[[Element], Text]
    tab_length: int
    block_level_elements: list[str]
    def __init__(
        self,
        *,
        extensions: Sequence[str | Extension] | None = ...,
        extension_configs: Mapping[str, Mapping[str, Any]] | None = ...,
        output_format: Literal["xhtml", "html"] | None = ...,
        tab_length: int | None = ...,
    ) -> None: ...
    def build_parser(self) -> Markdown: ...
    def registerExtensions(self, extensions: Sequence[Extension | str], configs: Mapping[str, Mapping[str, Any]]) -> Markdown: ...
    def build_extension(self, ext_name: Text, configs: Mapping[str, str]) -> Extension: ...
    def registerExtension(self, extension: Extension) -> Markdown: ...
    def reset(self: Self) -> Self: ...
    def set_output_format(self, format: Literal["xhtml", "html"]) -> Markdown: ...
    def is_block_level(self, tag: str) -> bool: ...
    def convert(self, source: Text) -> Text: ...
    def convertFile(
        self,
        input: str | TextIO | BinaryIO | None = ...,
        output: str | TextIO | BinaryIO | None = ...,
        encoding: str | None = ...,
    ) -> Markdown: ...

def markdown(
    text: Text,
    *,
    extensions: Sequence[str | Extension] | None = ...,
    extension_configs: Mapping[str, Mapping[str, Any]] | None = ...,
    output_format: Literal["xhtml", "html"] | None = ...,
    tab_length: int | None = ...,
) -> Text: ...
def markdownFromFile(
    *,
    input: str | TextIO | BinaryIO | None = ...,
    output: str | TextIO | BinaryIO | None = ...,
    encoding: str | None = ...,
    extensions: Sequence[str | Extension] | None = ...,
    extension_configs: Mapping[str, Mapping[str, Any]] | None = ...,
    output_format: Literal["xhtml", "html"] | None = ...,
    tab_length: int | None = ...,
) -> None: ...
