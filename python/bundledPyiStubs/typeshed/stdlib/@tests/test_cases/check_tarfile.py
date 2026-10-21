import lzma
import tarfile

with tarfile.open("test.tar.xz", "w:xz") as tar:
    pass

# Test with valid preset values
tarfile.open("test.tar.xz", "w:xz", preset=0)
tarfile.open("test.tar.xz", "w:xz", preset=5)
tarfile.open("test.tar.xz", "w:xz", preset=9)

# The preset may be combined with PRESET_EXTREME, as in lzma and TarFile.xzopen
tarfile.open("test.tar.xz", "w:xz", preset=9 | lzma.PRESET_EXTREME)

# Test pipe modes
tarfile.open("test.tar.xz", "r|*")
tarfile.open("test.tar.xz", mode="r|*")
