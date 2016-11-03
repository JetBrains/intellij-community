# Stubs for rlcompleter

from typing import Optional, Union
import sys

if sys.version_info >= (3,):
    _Text = str
else:
    _Text = Union[str, unicode]


class Completer:
    def complete(self, text: _Text, state: int) -> Optional[str]: ...
