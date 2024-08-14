from _typeshed import StrOrBytesPath, SupportsAllComparisons
from collections.abc import Callable

# This is intentionally duplicated and placed above the _pygit2
# star-import to workaround mypy issue #16972, so that consumers
# of the stubs see the correct, wrapped type for the name
# "Repository".
from .repository import Repository as Repository  # isort: skip

from . import enums
from ._build import __version__ as __version__
from ._pygit2 import *  # type: ignore[assignment]
from .blame import Blame as Blame, BlameHunk as BlameHunk
from .blob import BlobIO as BlobIO
from .callbacks import (
    CheckoutCallbacks as CheckoutCallbacks,
    Payload as Payload,
    RemoteCallbacks,
    StashApplyCallbacks as StashApplyCallbacks,
    get_credentials as get_credentials,
)
from .config import Config as Config
from .credentials import *
from .errors import Passthrough as Passthrough
from .filter import Filter as Filter
from .index import Index as Index, IndexEntry as IndexEntry
from .legacyenums import *
from .packbuilder import PackBuilder as PackBuilder
from .remotes import Remote as Remote
from .repository import Repository as Repository  # noqa: F811 # intentional workaround
from .settings import Settings
from .submodules import Submodule as Submodule

features: enums.Feature
LIBGIT2_VER: tuple[int, int, int]

def init_repository(
    path: StrOrBytesPath | None,
    bare: bool = False,
    flags: enums.RepositoryInitFlag = ...,
    mode: int | enums.RepositoryInitMode = ...,
    workdir_path: str | None = None,
    description: str | None = None,
    template_path: str | None = None,
    initial_head: str | None = None,
    origin_url: str | None = None,
) -> Repository: ...
def clone_repository(
    url: str,
    path: str,
    bare: bool = False,
    repository: Callable[[str, bool], Repository] | None = None,
    remote: Callable[[Repository, str, str], Remote] | None = None,
    checkout_branch: str | None = None,
    callbacks: RemoteCallbacks | None = None,
    depth: int = 0,
) -> Repository: ...

tree_entry_key: Callable[[Object], SupportsAllComparisons]  # functools.cmp_to_key(tree_entry_cmp)
settings: Settings
