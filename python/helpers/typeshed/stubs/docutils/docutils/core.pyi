from _typeshed import Incomplete

from docutils import SettingsSpec
from docutils.writers import _WriterParts

__docformat__: str

class Publisher:
    document: Incomplete
    reader: Incomplete
    parser: Incomplete
    writer: Incomplete
    source: Incomplete
    source_class: Incomplete
    destination: Incomplete
    destination_class: Incomplete
    settings: Incomplete
    def __init__(
        self,
        reader=None,
        parser=None,
        writer=None,
        source=None,
        source_class=...,
        destination=None,
        destination_class=...,
        settings=None,
    ) -> None: ...
    def set_reader(self, reader_name, parser, parser_name) -> None: ...
    def set_writer(self, writer_name) -> None: ...
    def set_components(self, reader_name, parser_name, writer_name) -> None: ...
    def setup_option_parser(self, usage=None, description=None, settings_spec=None, config_section=None, **defaults): ...
    def get_settings(
        self,
        usage: str | None = None,
        description: str | None = None,
        settings_spec: SettingsSpec | None = None,
        config_section: str | None = None,
        **defaults,
    ): ...
    def process_programmatic_settings(self, settings_spec, settings_overrides, config_section) -> None: ...
    def process_command_line(
        self, argv=None, usage=None, description=None, settings_spec=None, config_section=None, **defaults
    ) -> None: ...
    def set_io(self, source_path=None, destination_path=None) -> None: ...
    def set_source(self, source=None, source_path=None) -> None: ...
    def set_destination(self, destination=None, destination_path=None) -> None: ...
    def apply_transforms(self) -> None: ...
    def publish(
        self,
        argv=None,
        usage=None,
        description=None,
        settings_spec=None,
        settings_overrides=None,
        config_section=None,
        enable_exit_status: bool = False,
    ): ...
    def debugging_dumps(self) -> None: ...
    def prompt(self) -> None: ...
    def report_Exception(self, error) -> None: ...
    def report_SystemMessage(self, error) -> None: ...
    def report_UnicodeError(self, error) -> None: ...

default_usage: str
default_description: str

def publish_cmdline(
    reader=None,
    reader_name: str = "standalone",
    parser=None,
    parser_name: str = "restructuredtext",
    writer=None,
    writer_name: str = "pseudoxml",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = True,
    argv=None,
    usage=...,
    description=...,
): ...
def publish_file(
    source=None,
    source_path=None,
    destination=None,
    destination_path=None,
    reader=None,
    reader_name: str = "standalone",
    parser=None,
    parser_name: str = "restructuredtext",
    writer=None,
    writer_name: str = "pseudoxml",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = False,
): ...
def publish_string(
    source,
    source_path=None,
    destination_path=None,
    reader=None,
    reader_name: str = "standalone",
    parser=None,
    parser_name: str = "restructuredtext",
    writer=None,
    writer_name: str = "pseudoxml",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = False,
): ...
def publish_parts(
    source,
    source_path=None,
    source_class=...,
    destination_path=None,
    reader=None,
    reader_name: str = "standalone",
    parser=None,
    parser_name: str = "restructuredtext",
    writer=None,
    writer_name: str = "pseudoxml",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = False,
) -> _WriterParts: ...
def publish_doctree(
    source,
    source_path=None,
    source_class=...,
    reader=None,
    reader_name: str = "standalone",
    parser=None,
    parser_name: str = "restructuredtext",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = False,
): ...
def publish_from_doctree(
    document,
    destination_path=None,
    writer=None,
    writer_name: str = "pseudoxml",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = False,
): ...
def publish_cmdline_to_binary(
    reader=None,
    reader_name: str = "standalone",
    parser=None,
    parser_name: str = "restructuredtext",
    writer=None,
    writer_name: str = "pseudoxml",
    settings=None,
    settings_spec=None,
    settings_overrides=None,
    config_section=None,
    enable_exit_status: bool = True,
    argv=None,
    usage=...,
    description=...,
    destination=None,
    destination_class=...,
): ...
def publish_programmatically(
    source_class,
    source,
    source_path,
    destination_class,
    destination,
    destination_path,
    reader,
    reader_name,
    parser,
    parser_name,
    writer,
    writer_name,
    settings,
    settings_spec,
    settings_overrides,
    config_section,
    enable_exit_status,
): ...
def rst2something(writer, documenttype, doc_path: str = "") -> None: ...
def rst2html() -> None: ...
def rst2html4() -> None: ...
def rst2html5() -> None: ...
def rst2latex() -> None: ...
def rst2man() -> None: ...
def rst2odt() -> None: ...
def rst2pseudoxml() -> None: ...
def rst2s5() -> None: ...
def rst2xetex() -> None: ...
def rst2xml() -> None: ...
