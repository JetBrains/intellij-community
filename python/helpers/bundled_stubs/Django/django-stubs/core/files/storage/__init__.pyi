from django.utils.functional import LazyObject

from .base import Storage
from .filesystem import FileSystemStorage
from .handler import InvalidStorageError, StorageHandler
from .memory import InMemoryStorage

__all__ = (
    "DefaultStorage",
    "FileSystemStorage",
    "InMemoryStorage",
    "InvalidStorageError",
    "Storage",
    "StorageHandler",
    "default_storage",
    "storages",
)

class DefaultStorage(LazyObject): ...

storages: StorageHandler
# default_storage is actually an instance of DefaultStorage, but it proxies through to a Storage
default_storage: Storage
