from _typeshed import Incomplete
from abc import ABCMeta

from hvac.api.vault_api_base import VaultApiBase

logger: Incomplete

class SystemBackendMixin(VaultApiBase, metaclass=ABCMeta): ...
