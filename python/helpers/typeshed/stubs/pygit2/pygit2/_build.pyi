from pathlib import Path

__version__: str

def get_libgit2_paths() -> tuple[Path, dict[str, list[str]]]: ...
