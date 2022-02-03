from distutils.util import Mixin2to3 as _Mixin2to3
from lib2to3.refactor import RefactoringTool

class DistutilsRefactoringTool(RefactoringTool):
    def log_error(self, msg, *args, **kw) -> None: ...  # type: ignore[override]
    def log_message(self, msg, *args) -> None: ...
    def log_debug(self, msg, *args) -> None: ...

class Mixin2to3(_Mixin2to3):
    def run_2to3(self, files, doctests: bool = ...) -> None: ...
