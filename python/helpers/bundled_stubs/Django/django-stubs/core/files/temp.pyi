import tempfile

NamedTemporaryFile = tempfile.NamedTemporaryFile

gettempdir = tempfile.gettempdir

__all__ = ("NamedTemporaryFile", "gettempdir")
