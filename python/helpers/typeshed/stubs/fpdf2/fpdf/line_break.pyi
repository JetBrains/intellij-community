from _typeshed import Incomplete
from collections.abc import Sequence
from typing import NamedTuple

from .enums import WrapMode

SOFT_HYPHEN: str
HYPHEN: str
SPACE: str
NEWLINE: str

class Fragment:
    characters: list[str]
    graphics_state: dict[str, Incomplete]
    k: float
    url: str | None
    def __init__(
        self, characters: list[str] | str, graphics_state: dict[str, Incomplete], k: float, url: str | None = None
    ) -> None: ...
    @property
    def font(self): ...
    @font.setter
    def font(self, v) -> None: ...
    @property
    def is_ttf_font(self): ...
    @property
    def font_style(self): ...
    @property
    def font_family(self): ...
    @property
    def font_size_pt(self): ...
    @property
    def font_size(self): ...
    @property
    def font_stretching(self): ...
    @property
    def char_spacing(self): ...
    @property
    def text_mode(self): ...
    @property
    def underline(self): ...
    @property
    def draw_color(self): ...
    @property
    def fill_color(self): ...
    @property
    def text_color(self): ...
    @property
    def line_width(self): ...
    @property
    def char_vpos(self): ...
    @property
    def lift(self): ...
    @property
    def string(self): ...
    def trim(self, index: int): ...
    def __eq__(self, other: Fragment) -> bool: ...  # type: ignore[override]
    def get_width(self, start: int = 0, end: int | None = None, chars: str | None = None, initial_cs: bool = True): ...
    def get_character_width(self, character: str, print_sh: bool = False, initial_cs: bool = True): ...

class TextLine(NamedTuple):
    fragments: tuple[Incomplete, ...]
    text_width: float
    number_of_spaces: int
    justify: bool
    trailing_nl: bool = ...

class SpaceHint(NamedTuple):
    original_fragment_index: int
    original_character_index: int
    current_line_fragment_index: int
    current_line_character_index: int
    line_width: float
    number_of_spaces: int

class HyphenHint(NamedTuple):
    original_fragment_index: int
    original_character_index: int
    current_line_fragment_index: int
    current_line_character_index: int
    line_width: float
    number_of_spaces: int
    curchar: str
    curchar_width: float
    graphics_state: dict[str, Incomplete]
    k: float

class CurrentLine:
    print_sh: Incomplete
    fragments: Incomplete
    width: int
    number_of_spaces: int
    space_break_hint: Incomplete
    hyphen_break_hint: Incomplete
    def __init__(self, print_sh: bool = False) -> None: ...
    def add_character(
        self,
        character: str,
        character_width: float,
        graphics_state: dict[str, Incomplete],
        k: float,
        original_fragment_index: int,
        original_character_index: int,
        url: str | None = None,
    ): ...
    def trim_trailing_spaces(self) -> None: ...
    def manual_break(self, justify: bool = False, trailing_nl: bool = False): ...
    def automatic_break_possible(self): ...
    def automatic_break(self, justify: bool): ...

class MultiLineBreak:
    styled_text_fragments: Sequence[Fragment]
    justify: bool
    print_sh: bool
    wrap_mode: WrapMode
    fragment_index: int
    character_index: int
    idx_last_forced_break: int | None
    def __init__(
        self, styled_text_fragments: Sequence[Fragment], justify: bool = False, print_sh: bool = False, wrapmode: WrapMode = ...
    ) -> None: ...
    def get_line_of_given_width(self, maximum_width: float, wordsplit: bool = True): ...
