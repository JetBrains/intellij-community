from typing import Any, Optional

def build_inlinepatterns(md, **kwargs): ...

NOIMG: str
BACKTICK_RE: str
ESCAPE_RE: str
EMPHASIS_RE: str
STRONG_RE: str
SMART_STRONG_RE: str
SMART_EMPHASIS_RE: str
SMART_STRONG_EM_RE: str
EM_STRONG_RE: str
EM_STRONG2_RE: str
STRONG_EM_RE: str
STRONG_EM2_RE: str
STRONG_EM3_RE: str
LINK_RE: str
IMAGE_LINK_RE: str
REFERENCE_RE: str
IMAGE_REFERENCE_RE: str
NOT_STRONG_RE: str
AUTOLINK_RE: str
AUTOMAIL_RE: str
HTML_RE: str
ENTITY_RE: str
LINE_BREAK_RE: str

def dequote(string): ...

class EmStrongItem: ...

class Pattern:
    ANCESTOR_EXCLUDES: Any
    pattern: Any
    compiled_re: Any
    md: Any
    def __init__(self, pattern, md: Optional[Any] = ...) -> None: ...
    @property
    def markdown(self): ...
    def getCompiledRegExp(self): ...
    def handleMatch(self, m) -> None: ...
    def type(self): ...
    def unescape(self, text): ...

class InlineProcessor(Pattern):
    pattern: Any
    compiled_re: Any
    safe_mode: bool = ...
    md: Any
    def __init__(self, pattern, md: Optional[Any] = ...) -> None: ...
    def handleMatch(self, m, data) -> None: ...  # type: ignore

class SimpleTextPattern(Pattern):
    def handleMatch(self, m): ...

class SimpleTextInlineProcessor(InlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore

class EscapeInlineProcessor(InlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore

class SimpleTagPattern(Pattern):
    tag: Any
    def __init__(self, pattern, tag) -> None: ...
    def handleMatch(self, m): ...

class SimpleTagInlineProcessor(InlineProcessor):
    tag: Any
    def __init__(self, pattern, tag) -> None: ...
    def handleMatch(self, m, data): ...  # type: ignore

class SubstituteTagPattern(SimpleTagPattern):
    def handleMatch(self, m): ...

class SubstituteTagInlineProcessor(SimpleTagInlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore

class BacktickInlineProcessor(InlineProcessor):
    ESCAPED_BSLASH: Any
    tag: str = ...
    def __init__(self, pattern) -> None: ...
    def handleMatch(self, m, data): ...  # type: ignore

class DoubleTagPattern(SimpleTagPattern):
    def handleMatch(self, m): ...

class DoubleTagInlineProcessor(SimpleTagInlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore

class HtmlInlineProcessor(InlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore
    def unescape(self, text): ...

class AsteriskProcessor(InlineProcessor):
    PATTERNS: Any
    def build_single(self, m, tag, idx): ...
    def build_double(self, m, tags, idx): ...
    def build_double2(self, m, tags, idx): ...
    def parse_sub_patterns(self, data, parent, last, idx) -> None: ...
    def build_element(self, m, builder, tags, index): ...
    def handleMatch(self, m, data): ...  # type: ignore

class UnderscoreProcessor(AsteriskProcessor):
    PATTERNS: Any

class LinkInlineProcessor(InlineProcessor):
    RE_LINK: Any
    RE_TITLE_CLEAN: Any
    def handleMatch(self, m, data): ...  # type: ignore
    def getLink(self, data, index): ...
    def getText(self, data, index): ...

class ImageInlineProcessor(LinkInlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore

class ReferenceInlineProcessor(LinkInlineProcessor):
    NEWLINE_CLEANUP_RE: Pattern
    RE_LINK: Any
    def handleMatch(self, m, data): ...  # type: ignore
    def evalId(self, data, index, text): ...
    def makeTag(self, href, title, text): ...

class ShortReferenceInlineProcessor(ReferenceInlineProcessor):
    def evalId(self, data, index, text): ...

class ImageReferenceInlineProcessor(ReferenceInlineProcessor):
    def makeTag(self, href, title, text): ...

class AutolinkInlineProcessor(InlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore

class AutomailInlineProcessor(InlineProcessor):
    def handleMatch(self, m, data): ...  # type: ignore
