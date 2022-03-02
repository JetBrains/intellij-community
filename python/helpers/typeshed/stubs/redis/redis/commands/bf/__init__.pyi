from typing import Any

from .commands import *
from .info import BFInfo as BFInfo, CFInfo as CFInfo, CMSInfo as CMSInfo, TDigestInfo as TDigestInfo, TopKInfo as TopKInfo

class AbstractBloom:
    @staticmethod
    def appendItems(params, items) -> None: ...
    @staticmethod
    def appendError(params, error) -> None: ...
    @staticmethod
    def appendCapacity(params, capacity) -> None: ...
    @staticmethod
    def appendExpansion(params, expansion) -> None: ...
    @staticmethod
    def appendNoScale(params, noScale) -> None: ...
    @staticmethod
    def appendWeights(params, weights) -> None: ...
    @staticmethod
    def appendNoCreate(params, noCreate) -> None: ...
    @staticmethod
    def appendItemsAndIncrements(params, items, increments) -> None: ...
    @staticmethod
    def appendValuesAndWeights(params, items, weights) -> None: ...
    @staticmethod
    def appendMaxIterations(params, max_iterations) -> None: ...
    @staticmethod
    def appendBucketSize(params, bucket_size) -> None: ...

class CMSBloom(CMSCommands, AbstractBloom):
    client: Any
    commandmixin: Any
    execute_command: Any
    def __init__(self, client, **kwargs) -> None: ...

class TOPKBloom(TOPKCommands, AbstractBloom):
    client: Any
    commandmixin: Any
    execute_command: Any
    def __init__(self, client, **kwargs) -> None: ...

class CFBloom(CFCommands, AbstractBloom):
    client: Any
    commandmixin: Any
    execute_command: Any
    def __init__(self, client, **kwargs) -> None: ...

class TDigestBloom(TDigestCommands, AbstractBloom):
    client: Any
    commandmixin: Any
    execute_command: Any
    def __init__(self, client, **kwargs) -> None: ...

class BFBloom(BFCommands, AbstractBloom):
    client: Any
    commandmixin: Any
    execute_command: Any
    def __init__(self, client, **kwargs) -> None: ...
