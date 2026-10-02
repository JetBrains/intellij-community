from _typeshed import Incomplete
from typing import Final, NamedTuple

from .api import Request, Response
from .struct import Struct

DEFAULT_GENERATION_ID: Final = -1
UNKNOWN_MEMBER_ID: Final = ""

class GroupMember(NamedTuple):
    member_id: Incomplete = ...
    group_instance_id: Incomplete = ...
    metadata: Incomplete = ...

class JoinGroupResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupResponse_v5(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class JoinGroupRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

JoinGroupRequest: Incomplete
JoinGroupResponse: Incomplete

class ProtocolMetadata(Struct):
    SCHEMA: Incomplete

class SyncGroupResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class SyncGroupRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

SyncGroupRequest: Incomplete
SyncGroupResponse: Incomplete

class MemberAssignment(Struct):
    SCHEMA: Incomplete

class HeartbeatResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class HeartbeatResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class HeartbeatResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class HeartbeatResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class HeartbeatRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class HeartbeatRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class HeartbeatRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class HeartbeatRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

HeartbeatRequest: Incomplete
HeartbeatResponse: Incomplete

class LeaveGroupResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class LeaveGroupResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class LeaveGroupResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class LeaveGroupResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class LeaveGroupRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class LeaveGroupRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class LeaveGroupRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class LeaveGroupRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

LeaveGroupRequest: Incomplete
LeaveGroupResponse: Incomplete
