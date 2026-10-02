from _io import BufferedReader, BufferedRWPair, BufferedWriter
from gzip import GzipFile
from io import FileIO, RawIOBase, TextIOWrapper
from socket import SocketIO
from typing import Any
from typing_extensions import assert_type

socket: Any = None

BufferedReader(RawIOBase())
BufferedWriter(RawIOBase())
BufferedWriter(SocketIO(socket, "r"))

BufferedRWPair(open("", "rb"), open("", "wb"))

assert_type(TextIOWrapper(FileIO("")).buffer, FileIO)
assert_type(TextIOWrapper(FileIO(13)).detach(), FileIO)
assert_type(TextIOWrapper(GzipFile("")).buffer, GzipFile)
