from _typeshed import Incomplete

from kafka.protocol.struct import Struct

class ConsumerProtocolMemberMetadata_v0(Struct):
    SCHEMA: Incomplete

class ConsumerProtocolMemberAssignment_v0(Struct):
    SCHEMA: Incomplete
    def partitions(self): ...

class ConsumerProtocol_v0:
    PROTOCOL_TYPE: str
    METADATA = ConsumerProtocolMemberMetadata_v0
    ASSIGNMENT = ConsumerProtocolMemberAssignment_v0

ConsumerProtocol: Incomplete
