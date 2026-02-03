from re import Match, Pattern
from typing import Any

from django.core.management.base import BaseCommand
from django.utils.functional import cached_property

plural_forms_re: Pattern[str]
STATUS_OK: int
NO_LOCALE_DIR: Any

def check_programs(*programs: str) -> None: ...
def is_valid_locale(locale: str) -> Match[str] | None: ...

class TranslatableFile:
    dirpath: str
    file_name: str
    locale_dir: str
    def __init__(self, dirpath: str, file_name: str, locale_dir: str | None) -> None: ...

class BuildFile:
    """
    Represent the state of a translatable file during the build process.
    """

    command: BaseCommand
    domain: str
    translatable: TranslatableFile
    def __init__(self, command: BaseCommand, domain: str, translatable: TranslatableFile) -> None: ...
    @cached_property
    def is_templatized(self) -> bool: ...
    @cached_property
    def path(self) -> str: ...
    @cached_property
    def work_path(self) -> str: ...
    def preprocess(self) -> None: ...
    def postprocess_messages(self, msgs: str) -> str: ...
    def cleanup(self) -> None: ...

def normalize_eols(raw_contents: str) -> str: ...
def write_pot_file(potfile: str, msgs: str) -> None: ...

class Command(BaseCommand):
    translatable_file_class: type[TranslatableFile]
    build_file_class: type[BuildFile]
    msgmerge_options: list[str]
    msguniq_options: list[str]
    msgattrib_options: list[str]
    xgettext_options: list[str]

    domain: str
    verbosity: int
    symlinks: bool
    ignore_patterns: list[str]
    no_obsolete: bool
    keep_pot: bool
    extensions: set[str]
    invoked_for_django: bool
    locale_paths: list[str]
    default_locale_path: str | None
    @cached_property
    def gettext_version(self) -> tuple[int, int] | tuple[int, int, int]: ...
    @cached_property
    def settings_available(self) -> bool: ...
    def build_potfiles(self) -> list[str]: ...
    def remove_potfiles(self) -> None: ...
    def find_files(self, root: str) -> list[TranslatableFile]: ...
    def process_files(self, file_list: list[TranslatableFile]) -> None: ...
    def process_locale_dir(self, locale_dir: str, files: list[TranslatableFile]) -> None: ...
    def write_po_file(self, potfile: str, locale: str) -> None: ...
    def copy_plural_forms(self, msgs: str, locale: str) -> str: ...
