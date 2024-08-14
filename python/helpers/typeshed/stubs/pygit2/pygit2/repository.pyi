from _typeshed import StrOrBytesPath
from collections.abc import Callable, Iterator
from tarfile import TarInfo
from typing import IO, Any, Protocol
from typing_extensions import TypeAlias

from ._pygit2 import Blob, Commit, Diff, Object, Oid, Reference, Repository as _Repository, Signature, Tree, _OidArg
from .blame import Blame
from .branches import Branches
from .callbacks import CheckoutCallbacks, StashApplyCallbacks
from .config import Config
from .enums import (
    AttrCheck,
    BlameFlag,
    BranchType as BranchType,
    CheckoutStrategy,
    DescribeStrategy,
    DiffOption,
    MergeFavor,
    MergeFileFlag,
    MergeFlag,
    RepositoryOpenFlag,
    RepositoryState,
)
from .index import Index, IndexEntry
from .packbuilder import PackBuilder
from .references import References
from .remotes import RemoteCollection
from .submodules import SubmoduleCollection
from .utils import _IntoStrArray

_PackDelegate: TypeAlias = Callable[[PackBuilder], None]

class _SupportsAddfile(Protocol):
    def addfile(self, tarinfo: TarInfo, fileobj: IO[bytes] | None = None) -> None: ...

class BaseRepository(_Repository):
    branches: Branches
    references: References
    remotes: RemoteCollection
    submodules: SubmoduleCollection

    def __init__(self, *args: Any, **kwargs: Any) -> None: ...  # not meant for direct use
    def read(self, oid: _OidArg) -> tuple[int, int, bytes]: ...
    def write(self, type: int, data: bytes) -> Oid: ...
    def pack(
        self, path: StrOrBytesPath | None = None, pack_delegate: _PackDelegate | None = None, n_threads: int | None = None
    ) -> int: ...
    def __iter__(self) -> Iterator[Oid]: ...
    def get(self, key: _OidArg, default: Object | None = None) -> Object | None: ...
    def __getitem__(self, key: _OidArg) -> Object: ...
    def __contains__(self, key: _OidArg) -> bool: ...
    @property
    def config(self) -> Config: ...
    @property
    def config_snapshot(self) -> Config: ...
    def create_reference(self, name: str, target: _OidArg, force: bool = False, message: str | None = None) -> Reference: ...
    def listall_references(self) -> list[str]: ...
    def listall_reference_objects(self) -> list[Reference]: ...
    def resolve_refish(self, refish: str) -> tuple[Commit, Reference]: ...
    def checkout_head(
        self,
        *,
        callbacks: CheckoutCallbacks | None = None,
        strategy: CheckoutStrategy | None = None,
        directory: str | None = None,
        paths: _IntoStrArray = None,
    ) -> None: ...
    def checkout_index(
        self,
        index: Index | None = None,
        *,
        callbacks: CheckoutCallbacks | None = None,
        strategy: CheckoutStrategy | None = None,
        directory: str | None = None,
        paths: _IntoStrArray = None,
    ) -> None: ...
    def checkout_tree(
        self,
        treeish: Object,
        *,
        callbacks: CheckoutCallbacks | None = None,
        strategy: CheckoutStrategy | None = None,
        directory: str | None = None,
        paths: _IntoStrArray = None,
    ) -> None: ...
    def checkout(
        self,
        refname: str | Reference | None = None,
        *,
        callbacks: CheckoutCallbacks | None = None,
        strategy: CheckoutStrategy | None = None,
        directory: str | None = None,
        paths: _IntoStrArray = None,
    ) -> None: ...
    def set_head(self, target: _OidArg) -> None: ...
    def diff(
        self,
        a: bytes | str | Oid | Blob | Tree | None = None,
        b: bytes | str | Oid | Blob | Tree | None = None,
        cached: bool = False,
        flags: DiffOption = ...,
        context_lines: int = 3,
        interhunk_lines: int = 0,
    ) -> Diff: ...
    def state(self) -> RepositoryState: ...
    def state_cleanup(self) -> None: ...
    def blame(
        self,
        path: StrOrBytesPath,
        flags: BlameFlag = ...,
        min_match_characters: int | None = None,
        newest_commit: _OidArg | None = None,
        oldest_commit: _OidArg | None = None,
        min_line: int | None = None,
        max_line: int | None = None,
    ) -> Blame: ...
    @property
    def index(self) -> Index: ...
    def merge_file_from_index(self, ancestor: IndexEntry | None, ours: IndexEntry | None, theirs: IndexEntry | None) -> str: ...
    def merge_commits(
        self,
        ours: str | Oid | Commit,
        theirs: str | Oid | Commit,
        favor: MergeFavor = ...,
        flags: MergeFlag = ...,
        file_flags: MergeFileFlag = ...,
    ) -> Index: ...
    def merge_trees(
        self,
        ancestor: str | Oid | Tree,
        ours: str | Oid | Tree,
        theirs: str | Oid | Tree,
        favor: MergeFavor = ...,
        flags: MergeFlag = ...,
        file_flags: MergeFileFlag = ...,
    ) -> Index: ...
    def merge(self, id: Oid | str, favor: MergeFavor = ..., flags: MergeFlag = ..., file_flags: MergeFileFlag = ...) -> None: ...
    @property
    def raw_message(self) -> bytes: ...
    @property
    def message(self) -> str: ...
    def remove_message(self) -> None: ...
    def describe(
        self,
        committish: str | Reference | Commit | None = None,
        max_candidates_tags: int | None = None,
        describe_strategy: DescribeStrategy = ...,
        pattern: str | None = None,
        only_follow_first_parent: bool | None = None,
        show_commit_oid_as_fallback: bool | None = None,
        abbreviated_size: int | None = None,
        always_use_long_format: bool | None = None,
        dirty_suffix: str | None = None,
    ) -> str: ...
    def stash(
        self,
        stasher: Signature,
        message: str | None = None,
        keep_index: bool = False,
        include_untracked: bool = False,
        include_ignored: bool = False,
        keep_all: bool = False,
        paths: list[str] | None = None,
    ) -> Oid: ...
    def stash_apply(
        self,
        index: int = 0,
        *,
        callbacks: StashApplyCallbacks | None = None,
        reinstate_index: bool = False,
        strategy: CheckoutStrategy | None = None,
        directory: str | None = None,
        paths: _IntoStrArray = None,
    ) -> None: ...
    def stash_drop(self, index: int = 0) -> None: ...
    def stash_pop(
        self,
        index: int = 0,
        *,
        callbacks: StashApplyCallbacks | None = None,
        reinstate_index: bool = False,
        strategy: CheckoutStrategy | None = None,
        directory: str | None = None,
        paths: _IntoStrArray = None,
    ) -> None: ...
    def write_archive(
        self, treeish: _OidArg | Tree, archive: _SupportsAddfile, timestamp: int | None = None, prefix: str = ""
    ) -> None: ...
    def ahead_behind(self, local: _OidArg, upstream: _OidArg) -> tuple[int, int]: ...
    def get_attr(
        self, path: StrOrBytesPath, name: str | bytes, flags: AttrCheck = ..., commit: Oid | str | None = None
    ) -> bool | None | str: ...
    @property
    def ident(self) -> tuple[str, str]: ...
    def set_ident(self, name: bytes | str | None, email: bytes | str | None) -> None: ...
    def revert(self, commit: Commit) -> None: ...
    def revert_commit(self, revert_commit: Commit, our_commit: Commit, mainline: int = 0) -> Index: ...
    def amend_commit(
        self,
        commit: Commit | _OidArg,
        refname: Reference | str | None,
        author: Signature | None = None,
        committer: Signature | None = None,
        message: bytes | str | None = None,
        tree: Tree | _OidArg | None = None,
        encoding: str = "UTF-8",
    ) -> Oid: ...

class Repository(BaseRepository):
    def __init__(self, path: str | None = None, flags: RepositoryOpenFlag = ...) -> None: ...
