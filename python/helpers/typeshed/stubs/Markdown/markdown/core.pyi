from _typeshed import Self
from collections.abc import Callable, Mapping, Sequence
from typing import Any, BinaryIO, ClassVar, TextIO
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
    output_formats: ClassVar[dict[Literal["xhtml", "html"], Callable[[Element], str]]]
    output_format: Literal["xhtml", "html"]
    serializer: Callable[[Element], str]
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
    def build_extension(self, ext_name: str, configs: Mapping[str, str]) -> Extension: ...
    def registerExtension(self, extension: Extension) -> Markdown: ...
    def reset(self: Self) -> Self: ...
    def set_output_format(self, format: Literal["xhtml", "html"]) -> Markdown: ...
    def is_block_level(self, tag: str) -> bool: ...
    def convert(self, source: str) -> str: ...
    def convertFile(
        self,
        input: str | TextIO | BinaryIO | None = ...,
        output: str | TextIO | BinaryIO | None = ...,
        encoding: str | None = ...,
    ) -> Markdown: ...

def markdown(
    text: str,
    *,
    extensions: Sequence[str | Extension] | None = ...,
    extension_configs: Mapping[str, Mapping[str, Any]] | None = ...,
    output_format: Literal["xhtml", "html"] | None = ...,
    tab_length: int | None = ...,
) -> str: ...
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
