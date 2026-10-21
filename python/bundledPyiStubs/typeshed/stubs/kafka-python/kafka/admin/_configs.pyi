from _typeshed import Incomplete
from collections.abc import Mapping, Sequence
from enum import IntEnum

from kafka.util import EnumHelper

class ConfigAdminMixin:
    config: dict[Incomplete, Incomplete]
    def describe_configs(
        self,
        config_resources: Sequence[ConfigResource],
        include_synonyms: bool = False,
        config_filter: ConfigFilterType | str = "modified",
    ): ...
    def list_config_resources(self, resource_types: Sequence[ConfigResourceType | str] | None = None): ...
    def alter_configs(
        self,
        config_resources: Sequence[ConfigResource],
        validate_only: bool = False,
        raise_on_unknown: bool = True,
        incremental: bool | None = None,
    ): ...
    def reset_configs(
        self,
        config_resources: Sequence[ConfigResource],
        validate_only: bool = False,
        raise_on_unknown: bool = True,
        incremental: bool | None = None,
    ): ...

class AlterConfigOp(EnumHelper, IntEnum):
    SET = 0
    DELETE = 1
    APPEND = 2
    SUBTRACT = 3

class ConfigFilterType(EnumHelper, IntEnum):
    ALL = 0
    DYNAMIC = 1
    MODIFIED = 2
    DEFAULT = 3
    STATIC = 4
    def should_skip(self, config_source: ConfigSourceType) -> bool: ...

class ConfigResourceType(EnumHelper, IntEnum):
    UNKNOWN = 0
    TOPIC = 2
    BROKER = 4
    BROKER_LOGGER = 8
    CLIENT_METRICS = 16
    GROUP = 32

class ConfigResource:
    resource_type: ConfigResourceType
    name: str
    configs: Mapping[str, str] | None
    def __init__(self, resource_type: ConfigResourceType | str, name: str, configs: Mapping[str, str] | None = None) -> None: ...

class ConfigType(EnumHelper, IntEnum):
    UNKNOWN = 0
    BOOLEAN = 1
    STRING = 2
    INT = 3
    SHORT = 4
    LONG = 5
    DOUBLE = 6
    LIST = 7
    CLASS = 8
    PASSWORD = 9

class ConfigSourceType(EnumHelper, IntEnum):
    UNKNOWN = 0
    DYNAMIC_TOPIC_CONFIG = 1
    DYNAMIC_BROKER_CONFIG = 2
    DYNAMIC_DEFAULT_BROKER_CONFIG = 3
    STATIC_BROKER_CONFIG = 4
    DEFAULT_CONFIG = 5
    DYNAMIC_BROKER_LOGGER_CONFIG = 6
    DYNAMIC_CLIENT_METRICS_CONFIG = 7
    DYNAMIC_GROUP_CONFIG = 8
    def is_modified(self) -> bool: ...
    @classmethod
    def dynamic_for_resource_type(cls, resource_type: ConfigResourceType) -> ConfigSourceType: ...
