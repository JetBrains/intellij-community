from typing import Final

class TclTkInfo:
    TCL_ROOTNAME: Final = "_tcl_data"
    TK_ROOTNAME: Final = "_tk_data"
    def __init__(self) -> None: ...
    available: bool
    tkinter_extension_file: str | None
    tcl_version: tuple[int, int] | None
    tk_version: tuple[int, int] | None
    tcl_threaded: bool
    tcl_data_dir: str | None
    tk_data_dir: str | None
    tcl_module_dir: str | None
    is_macos_system_framework: bool
    tcl_shared_library: str | None
    tk_shared_library: str | None
    data_files: list[tuple[str, str, str]]

tcltk_info: TclTkInfo
