from collections.abc import Mapping
from enum import IntEnum

class ConfigResourceType(IntEnum):
    BROKER = 4
    TOPIC = 2

class ConfigResource:
    resource_type: ConfigResourceType
    name: str
    configs: Mapping[str, str] | None
    def __init__(self, resource_type: ConfigResourceType, name: str, configs: Mapping[str, str] | None = None) -> None: ...
