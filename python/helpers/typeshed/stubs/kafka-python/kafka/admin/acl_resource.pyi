from enum import IntEnum

class ResourceType(IntEnum):
    UNKNOWN = 0
    ANY = 1
    CLUSTER = 4
    DELEGATION_TOKEN = 6
    GROUP = 3
    TOPIC = 2
    TRANSACTIONAL_ID = 5

class ACLOperation(IntEnum):
    UNKNOWN = 0
    ANY = 1
    ALL = 2
    READ = 3
    WRITE = 4
    CREATE = 5
    DELETE = 6
    ALTER = 7
    DESCRIBE = 8
    CLUSTER_ACTION = 9
    DESCRIBE_CONFIGS = 10
    ALTER_CONFIGS = 11
    IDEMPOTENT_WRITE = 12
    CREATE_TOKENS = 13
    DESCRIBE_TOKENS = 13

class ACLPermissionType(IntEnum):
    UNKNOWN = 0
    ANY = 1
    DENY = 2
    ALLOW = 3

class ACLResourcePatternType(IntEnum):
    UNKNOWN = 0
    ANY = 1
    MATCH = 2
    LITERAL = 3
    PREFIXED = 4

class ACLFilter:
    principal: str | None
    host: str | None
    operation: ACLOperation
    permission_type: ACLPermissionType
    resource_pattern: ResourcePatternFilter
    def __init__(
        self,
        principal: str | None,
        host: str | None,
        operation: ACLOperation,
        permission_type: ACLPermissionType,
        resource_pattern: ResourcePatternFilter,
    ) -> None: ...
    def validate(self) -> None: ...
    def __eq__(self, other): ...
    def __hash__(self): ...

class ACL(ACLFilter):
    resource_pattern: ResourcePattern
    def __init__(
        self,
        principal: str,
        host: str,
        operation: ACLOperation,
        permission_type: ACLPermissionType,
        resource_pattern: ResourcePattern,
    ) -> None: ...
    def validate(self) -> None: ...

class ResourcePatternFilter:
    resource_type: ResourceType
    resource_name: str | None
    pattern_type: ACLResourcePatternType
    def __init__(self, resource_type: ResourceType, resource_name: str | None, pattern_type: ACLResourcePatternType) -> None: ...
    def validate(self) -> None: ...
    def __eq__(self, other): ...
    def __hash__(self): ...

class ResourcePattern(ResourcePatternFilter):
    resource_name: str
    def __init__(
        self,
        resource_type: ResourceType,
        resource_name: str,
        pattern_type: ACLResourcePatternType = ACLResourcePatternType.LITERAL,
    ) -> None: ...
    def validate(self) -> None: ...

def valid_acl_operations(int_vals) -> set[ACLOperation]: ...
