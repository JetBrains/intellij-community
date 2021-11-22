import abc
from distutils.cmd import Command as _Command
from typing import Any

po_file_read_mode: Any

def listify_value(arg, split: Any | None = ...): ...

class Command(_Command, metaclass=abc.ABCMeta):
    as_args: Any
    multiple_value_options: Any
    boolean_options: Any
    option_aliases: Any
    option_choices: Any
    log: Any
    distribution: Any
    verbose: bool
    force: Any
    help: int
    finalized: int
    def __init__(self, dist: Any | None = ...) -> None: ...

class compile_catalog(Command):
    description: str
    user_options: Any
    boolean_options: Any
    domain: str
    directory: Any
    input_file: Any
    output_file: Any
    locale: Any
    use_fuzzy: bool
    statistics: bool
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self): ...

class extract_messages(Command):
    description: str
    user_options: Any
    boolean_options: Any
    as_args: str
    multiple_value_options: Any
    option_aliases: Any
    option_choices: Any
    charset: str
    keywords: Any
    no_default_keywords: bool
    mapping_file: Any
    no_location: bool
    add_location: Any
    omit_header: bool
    output_file: Any
    input_dirs: Any
    input_paths: Any
    width: Any
    no_wrap: bool
    sort_output: bool
    sort_by_file: bool
    msgid_bugs_address: Any
    copyright_holder: Any
    project: Any
    version: Any
    add_comments: Any
    strip_comments: bool
    include_lineno: bool
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...

def check_message_extractors(dist, name, value) -> None: ...

class init_catalog(Command):
    description: str
    user_options: Any
    boolean_options: Any
    output_dir: Any
    output_file: Any
    input_file: Any
    locale: Any
    domain: str
    no_wrap: bool
    width: Any
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...

class update_catalog(Command):
    description: str
    user_options: Any
    boolean_options: Any
    domain: str
    input_file: Any
    output_dir: Any
    output_file: Any
    omit_header: bool
    locale: Any
    width: Any
    no_wrap: bool
    ignore_obsolete: bool
    init_missing: bool
    no_fuzzy_matching: bool
    update_header_comment: bool
    previous: bool
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...

class CommandLineInterface:
    usage: str
    version: Any
    commands: Any
    command_classes: Any
    log: Any
    parser: Any
    def run(self, argv: Any | None = ...): ...

def main(): ...
def parse_mapping(fileobj, filename: Any | None = ...): ...
def parse_keywords(strings=...): ...
