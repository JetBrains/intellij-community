from _typeshed import StrPath, Unused

from .._distutils.command import install_lib as orig

class install_lib(orig.install_lib):
    def run(self) -> None: ...
    def get_exclusions(self): ...
    def copy_tree(
        self,
        infile: StrPath,
        outfile: str,
        preserve_mode: bool = True,
        preserve_times: bool = True,
        preserve_symlinks: bool = False,
        level: Unused = 1,
    ): ...
    def get_outputs(self): ...
