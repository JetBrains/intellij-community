from typing import Any, ClassVar

from .upload import upload

class upload_docs(upload):
    DEFAULT_REPOSITORY: ClassVar[str]
    description: ClassVar[str]
    user_options: ClassVar[list[tuple[str, str | None, str]]]
    boolean_options: ClassVar[list[str]]
    def has_sphinx(self): ...
    sub_commands: Any
    upload_dir: Any
    target_dir: Any
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def create_zipfile(self, filename) -> None: ...
    def run(self) -> None: ...
    def upload_file(self, filename) -> None: ...  # type: ignore[override]
