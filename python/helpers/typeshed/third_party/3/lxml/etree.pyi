# Hand-written stub for lxml.etree as used by mypy.report.
# This is *far* from complete, and the stubgen-generated ones crash mypy.
# Any use of `Any` below means I couldn't figure out the type.

import typing
from typing import Any, Dict, List, MutableMapping, Tuple, Union
from typing import Iterable, Iterator, SupportsBytes


# We do *not* want `typing.AnyStr` because it is a `TypeVar`, which is an
# unnecessary constraint. It seems reasonable to constrain each
# List/Dict argument to use one type consistently, though, and it is
# necessary in order to keep these brief.
AnyStr = Union[str, bytes]
ListAnyStr = Union[List[str], List[bytes]]
DictAnyStr = Union[Dict[str, str], Dict[bytes, bytes]]
Dict_Tuple2AnyStr_Any = Union[Dict[Tuple[str, str], Any], Tuple[bytes, bytes], Any]


class ElementChildIterator(Iterator['_Element']):
    def __iter__(self) -> 'ElementChildIterator': ...
    def __next__(self) -> '_Element': ...

class _Element(Iterable['_Element']):
    def addprevious(self, element: '_Element') -> None:
        ...

    attrib = ...  # type: MutableMapping[str, str]
    text = ...  # type: AnyStr
    tag = ...  # type: str
    def append(self, element: '_Element') -> '_Element': ...
    def __iter__(self) -> ElementChildIterator: ...

class ElementBase(_Element):
    ...

class _ElementTree:
    def write(self,
              file: Union[AnyStr, typing.IO],
              encoding: AnyStr = ...,
              method: AnyStr = ...,
              pretty_print: bool = ...,
              xml_declaration: Any = ...,
              with_tail: Any = ...,
              standalone: bool = ...,
              compression: int = ...,
              exclusive: bool = ...,
              with_comments: bool = ...,
              inclusive_ns_prefixes: ListAnyStr = ...) -> None:
        ...

class _XSLTResultTree(SupportsBytes):
    ...

class _XSLTQuotedStringParam:
    ...

class XMLParser:
    ...

class XMLSchema:
    def __init__(self,
                 etree: Union[_Element, _ElementTree] = ...,
                 file: Union[AnyStr, typing.IO] = ...) -> None:
        ...

    def assertValid(self,
                    etree: Union[_Element, _ElementTree]) -> None:
        ...

class XSLTAccessControl:
    ...

class XSLT:
    def __init__(self,
                 xslt_input: Union[_Element, _ElementTree],
                 extensions: Dict_Tuple2AnyStr_Any = ...,
                 regexp: bool = ...,
                 access_control: XSLTAccessControl = ...) -> None:
        ...

    def __call__(self,
                 _input: Union[_Element, _ElementTree],
                 profile_run: bool = ...,
                 **kwargs: Union[AnyStr, _XSLTQuotedStringParam]) -> _XSLTResultTree:
        ...

    @staticmethod
    def strparam(s: AnyStr) -> _XSLTQuotedStringParam:
        ...

def Element(_tag: AnyStr,
            attrib: DictAnyStr = ...,
            nsmap: DictAnyStr = ...,
            **extra: AnyStr) -> _Element:
    ...

def SubElement(_parent: _Element, _tag: AnyStr,
               attrib: DictAnyStr = ...,
               nsmap: DictAnyStr = ...,
               **extra: AnyStr) -> _Element:
    ...

def ElementTree(element: _Element = ...,
                file: Union[AnyStr, typing.IO] = ...,
                parser: XMLParser = ...) -> _ElementTree:
    ...

def ProcessingInstruction(target: AnyStr, text: AnyStr = ...) -> _Element:
    ...

def parse(source: Union[AnyStr, typing.IO],
          parser: XMLParser = ...,
          base_url: AnyStr = ...) -> _ElementTree:
    ...


def fromstring(text: AnyStr,
               parser: XMLParser = ...,
               *,
               base_url: AnyStr = ...) -> _Element: ...

def tostring(element_or_tree: Union[_Element, _ElementTree],
             encoding: Union[str, type] = ...,
             method: str = ...,
             xml_declaration: bool = ...,
             pretty_print: bool = ...,
             with_tail: bool = ...,
             standalone: bool = ...,
             doctype: str = ...,
             exclusive: bool = ...,
             with_comments: bool = ...,
             inclusive_ns_prefixes: Any = ...) -> AnyStr: ...


class _ErrorLog:
    ...


class Error(Exception):
    ...

class LxmlError(Error):
    def __init__(self, message: Any, error_log: _ErrorLog = ...) -> None: ...
    error_log = ...  # type: _ErrorLog

class DocumentInvalid(LxmlError):
    ...

class LxmlSyntaxError(LxmlError, SyntaxError):
    ...

class ParseError(LxmlSyntaxError):
    ...

class XMLSyntaxError(ParseError):
    ...


class _Validator:
    ...

class DTD(_Validator):
    def __init__(self,
                 file: Union[AnyStr, typing.IO] = ...,
                 *,
                 external_id: Any = ...) -> None: ...

    def assertValid(self, etree: _Element) -> None: ...
