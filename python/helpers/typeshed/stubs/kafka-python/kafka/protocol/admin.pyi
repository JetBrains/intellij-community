from _typeshed import Incomplete
from enum import IntEnum

from kafka.protocol.api import Request, Response

class CreateTopicsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreateTopicsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreateTopicsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreateTopicsResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreateTopicsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreateTopicsResponse_v0
    SCHEMA: Incomplete

class CreateTopicsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreateTopicsResponse_v1
    SCHEMA: Incomplete

class CreateTopicsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreateTopicsResponse_v2
    SCHEMA: Incomplete

class CreateTopicsRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreateTopicsResponse_v3
    SCHEMA: Incomplete

CreateTopicsRequest: Incomplete
CreateTopicsResponse: Incomplete

class DeleteTopicsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteTopicsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteTopicsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteTopicsResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteTopicsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteTopicsResponse_v0
    SCHEMA: Incomplete

class DeleteTopicsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteTopicsResponse_v1
    SCHEMA: Incomplete

class DeleteTopicsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteTopicsResponse_v2
    SCHEMA: Incomplete

class DeleteTopicsRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteTopicsResponse_v3
    SCHEMA: Incomplete

DeleteTopicsRequest: Incomplete
DeleteTopicsResponse: Incomplete

class DeleteRecordsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteRecordsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteRecordsResponse_v0
    SCHEMA: Incomplete

DeleteRecordsResponse: Incomplete
DeleteRecordsRequest: Incomplete

class ListGroupsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListGroupsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListGroupsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ListGroupsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListGroupsResponse_v0
    SCHEMA: Incomplete

class ListGroupsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListGroupsResponse_v1
    SCHEMA: Incomplete

class ListGroupsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListGroupsResponse_v2
    SCHEMA: Incomplete

ListGroupsRequest: Incomplete
ListGroupsResponse: Incomplete

class DescribeGroupsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeGroupsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeGroupsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeGroupsResponse_v3(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeGroupsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeGroupsResponse_v0
    SCHEMA: Incomplete

class DescribeGroupsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeGroupsResponse_v1
    SCHEMA: Incomplete

class DescribeGroupsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeGroupsResponse_v2
    SCHEMA: Incomplete

class DescribeGroupsRequest_v3(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeGroupsResponse_v3
    SCHEMA: Incomplete

DescribeGroupsRequest: Incomplete
DescribeGroupsResponse: Incomplete

class DescribeAclsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeAclsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeAclsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeAclsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeAclsResponse_v0
    SCHEMA: Incomplete

class DescribeAclsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeAclsResponse_v1
    SCHEMA: Incomplete

class DescribeAclsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeAclsResponse_v2
    SCHEMA: Incomplete

DescribeAclsRequest: Incomplete
DescribeAclsResponse: Incomplete

class CreateAclsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreateAclsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreateAclsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreateAclsResponse_v0
    SCHEMA: Incomplete

class CreateAclsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreateAclsResponse_v1
    SCHEMA: Incomplete

CreateAclsRequest: Incomplete
CreateAclsResponse: Incomplete

class DeleteAclsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteAclsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteAclsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteAclsResponse_v0
    SCHEMA: Incomplete

class DeleteAclsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteAclsResponse_v1
    SCHEMA: Incomplete

DeleteAclsRequest: Incomplete
DeleteAclsResponse: Incomplete

class AlterConfigsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AlterConfigsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class AlterConfigsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AlterConfigsResponse_v0
    SCHEMA: Incomplete

class AlterConfigsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AlterConfigsResponse_v1
    SCHEMA: Incomplete

AlterConfigsRequest: Incomplete
AlterConfigsResponse: Incomplete

class DescribeConfigsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeConfigsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeConfigsResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeConfigsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeConfigsResponse_v0
    SCHEMA: Incomplete

class DescribeConfigsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeConfigsResponse_v1
    SCHEMA: Incomplete

class DescribeConfigsRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeConfigsResponse_v2
    SCHEMA: Incomplete

DescribeConfigsRequest: Incomplete
DescribeConfigsResponse: Incomplete

class DescribeLogDirsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeLogDirsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeLogDirsResponse_v0
    SCHEMA: Incomplete

DescribeLogDirsResponse: Incomplete
DescribeLogDirsRequest: Incomplete

class SaslAuthenticateResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslAuthenticateResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class SaslAuthenticateRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SaslAuthenticateResponse_v0
    SCHEMA: Incomplete

class SaslAuthenticateRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = SaslAuthenticateResponse_v1
    SCHEMA: Incomplete

SaslAuthenticateRequest: Incomplete
SaslAuthenticateResponse: Incomplete

class CreatePartitionsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreatePartitionsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class CreatePartitionsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = CreatePartitionsResponse_v0
    SCHEMA: Incomplete

class CreatePartitionsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    RESPONSE_TYPE = CreatePartitionsResponse_v1

CreatePartitionsRequest: Incomplete
CreatePartitionsResponse: Incomplete

class DeleteGroupsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteGroupsResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DeleteGroupsRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteGroupsResponse_v0
    SCHEMA: Incomplete

class DeleteGroupsRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DeleteGroupsResponse_v1
    SCHEMA: Incomplete

DeleteGroupsRequest: Incomplete
DeleteGroupsResponse: Incomplete

class DescribeClientQuotasResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class DescribeClientQuotasRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = DescribeClientQuotasResponse_v0
    SCHEMA: Incomplete

DescribeClientQuotasRequest: Incomplete
DescribeClientQuotasResponse: Incomplete

class AlterPartitionReassignmentsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    FLEXIBLE_VERSION: bool

class AlterPartitionReassignmentsRequest_v0(Request):
    FLEXIBLE_VERSION: bool
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AlterPartitionReassignmentsResponse_v0
    SCHEMA: Incomplete

AlterPartitionReassignmentsRequest: Incomplete
AlterPartitionReassignmentsResponse: Incomplete

class ListPartitionReassignmentsResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    FLEXIBLE_VERSION: bool

class ListPartitionReassignmentsRequest_v0(Request):
    FLEXIBLE_VERSION: bool
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ListPartitionReassignmentsResponse_v0
    SCHEMA: Incomplete

ListPartitionReassignmentsRequest: Incomplete
ListPartitionReassignmentsResponse: Incomplete

class ElectLeadersResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ElectLeadersRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ElectLeadersResponse_v0
    SCHEMA: Incomplete

class ElectLeadersResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete

class ElectLeadersRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = ElectLeadersResponse_v1
    SCHEMA: Incomplete

class ElectionType(IntEnum):
    PREFERRED = 0
    UNCLEAN = 1

ElectLeadersRequest: Incomplete
ElectLeadersResponse: Incomplete
