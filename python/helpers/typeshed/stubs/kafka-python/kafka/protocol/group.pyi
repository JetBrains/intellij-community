from _typeshed import Incomplete
from typing import NamedTuple

from kafka.protocol.api import Request, Response
from kafka.protocol.struct import Struct

DEFAULT_GENERATION_ID: int
UNKNOWN_MEMBER_ID: str

class GroupMember(NamedTuple):
    member_id: Incomplete = ...
    group_instance_id: Incomplete = ...
    metadata_bytes: Incomplete = ...

class JoinGroupResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class JoinGroupResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class JoinGroupResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class JoinGroupResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class JoinGroupResponse_v4(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class JoinGroupResponse_v5(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class JoinGroupRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = JoinGroupResponse_v0
    SCHEMA: Incomplete

class JoinGroupRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = JoinGroupResponse_v1
    SCHEMA: Incomplete

class JoinGroupRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = JoinGroupResponse_v2
    SCHEMA: Incomplete

class JoinGroupRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = JoinGroupResponse_v3
    SCHEMA: Incomplete

class JoinGroupRequest_v4(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = JoinGroupResponse_v4
    SCHEMA: Incomplete

class JoinGroupRequest_v5(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = JoinGroupResponse_v5
    SCHEMA: Incomplete

JoinGroupRequest: Incomplete
JoinGroupResponse: Incomplete

class ProtocolMetadata(Struct):
    SCHEMA: Incomplete

class SyncGroupResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SyncGroupResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SyncGroupResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SyncGroupResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SyncGroupRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SyncGroupResponse_v0
    SCHEMA: Incomplete

class SyncGroupRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SyncGroupResponse_v1
    SCHEMA: Incomplete

class SyncGroupRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SyncGroupResponse_v2
    SCHEMA: Incomplete

class SyncGroupRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SyncGroupResponse_v3
    SCHEMA: Incomplete

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
    RESPONSE_TYPE = HeartbeatResponse_v0
    SCHEMA: Incomplete

class HeartbeatRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = HeartbeatResponse_v1
    SCHEMA: Incomplete

class HeartbeatRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = HeartbeatResponse_v2
    SCHEMA: Incomplete

class HeartbeatRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = HeartbeatResponse_v3
    SCHEMA: Incomplete

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
    RESPONSE_TYPE = LeaveGroupResponse_v0
    SCHEMA: Incomplete

class LeaveGroupRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = LeaveGroupResponse_v1
    SCHEMA: Incomplete

class LeaveGroupRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = LeaveGroupResponse_v2
    SCHEMA: Incomplete

class LeaveGroupRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = LeaveGroupResponse_v3
    SCHEMA: Incomplete

LeaveGroupRequest: Incomplete
LeaveGroupResponse: Incomplete
