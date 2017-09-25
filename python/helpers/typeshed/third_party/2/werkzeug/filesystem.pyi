from typing import Any

has_likely_buggy_unicode_filesystem = ...  # type: Any

class BrokenFilesystemWarning(RuntimeWarning, UnicodeWarning): ...

def get_filesystem_encoding(): ...
