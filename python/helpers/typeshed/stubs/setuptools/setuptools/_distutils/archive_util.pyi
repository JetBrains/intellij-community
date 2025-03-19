from _typeshed import StrOrBytesPath, StrPath
from typing import overload

@overload
def make_archive(
    base_name: str,
    format: str,
    root_dir: StrOrBytesPath | None = None,
    base_dir: str | None = None,
    verbose: bool = False,
    dry_run: bool = False,
    owner: str | None = None,
    group: str | None = None,
) -> str: ...
@overload
def make_archive(
    base_name: StrPath,
    format: str,
    root_dir: StrOrBytesPath,
    base_dir: str | None = None,
    verbose: bool = False,
    dry_run: bool = False,
    owner: str | None = None,
    group: str | None = None,
) -> str: ...
def make_tarball(
    base_name: str,
    base_dir: StrPath,
    compress: str | None = ...,
    verbose: bool = False,
    dry_run: bool = False,
    owner: str | None = ...,
    group: str | None = ...,
) -> str: ...
def make_zipfile(base_name: str, base_dir: str, verbose: bool = False, dry_run: bool = False) -> str: ...
