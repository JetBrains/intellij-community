from typing import Match, Optional, Pattern
from typing_extensions import assert_type


def test_search(str_pat: Pattern[str], bytes_pat: Pattern[bytes]) -> None:
    assert_type(str_pat.search("x"), Optional[Match[str]])
    assert_type(bytes_pat.search(b"x"), Optional[Match[bytes]])
    assert_type(bytes_pat.search(bytearray(b"x")), Optional[Match[bytes]])
