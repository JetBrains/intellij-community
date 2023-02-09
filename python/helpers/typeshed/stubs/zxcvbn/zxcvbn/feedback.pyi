from collections.abc import Sequence
from typing_extensions import Literal, TypedDict

from .matching import _Match

class _Feedback(TypedDict):
    warning: str
    suggestions: list[str]

def get_feedback(score: Literal[0, 1, 2, 3, 4], sequence: Sequence[_Match]) -> _Feedback: ...
def get_match_feedback(match: _Match, is_sole_match: bool) -> _Feedback: ...
def get_dictionary_match_feedback(match: _Match, is_sole_match: bool) -> _Feedback: ...
