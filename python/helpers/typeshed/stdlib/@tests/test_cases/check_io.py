from gzip import GzipFile
from io import FileIO, TextIOWrapper
from typing_extensions import assert_type

assert_type(TextIOWrapper(FileIO("")).buffer, FileIO)
assert_type(TextIOWrapper(FileIO(13)).detach(), FileIO)
assert_type(TextIOWrapper(GzipFile("")).buffer, GzipFile)
