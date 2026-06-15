from _typeshed import Incomplete

class KafkaError(RuntimeError):
    retriable: bool
    invalid_metadata: bool
    def __eq__(self, other): ...

class Cancelled(KafkaError):
    retriable: bool

class CommitFailedError(KafkaError):
    def __init__(self, *args) -> None: ...

class IllegalArgumentError(KafkaError): ...
class IllegalStateError(KafkaError): ...
class IncompatibleBrokerVersion(KafkaError): ...
class KafkaConfigurationError(KafkaError): ...

class KafkaConnectionError(KafkaError):
    retriable: bool
    invalid_metadata: bool

class KafkaProtocolError(KafkaError):
    retriable: bool

class CorrelationIdError(KafkaProtocolError):
    retriable: bool

class InvalidReceiveError(KafkaProtocolError): ...

class KafkaTimeoutError(KafkaError):
    retriable: bool

class MetadataEmptyBrokerList(KafkaError):
    retriable: bool

class NoBrokersAvailable(KafkaError):
    retriable: bool
    invalid_metadata: bool

class NoOffsetForPartitionError(KafkaError): ...

class NodeNotReadyError(KafkaError):
    retriable: bool

class QuotaViolationError(KafkaError): ...

class StaleMetadata(KafkaError):
    retriable: bool
    invalid_metadata: bool

class TooManyInFlightRequests(KafkaError):
    retriable: bool

class UnrecognizedBrokerVersion(KafkaError): ...
class UnsupportedCodecError(KafkaError): ...
class TransactionAbortedError(KafkaError): ...

class BrokerResponseError(KafkaError):
    errno: Incomplete
    message: Incomplete
    description: Incomplete

class AuthorizationError(BrokerResponseError): ...

class NoError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownError(BrokerResponseError):
    errno: int
    message: str
    description: str

class OffsetOutOfRangeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class CorruptRecordError(BrokerResponseError):
    errno: int
    message: str
    description: str

CorruptRecordException = CorruptRecordError

class UnknownTopicOrPartitionError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class InvalidFetchRequestError(BrokerResponseError):
    errno: int
    message: str
    description: str

class LeaderNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class NotLeaderForPartitionError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class RequestTimedOutError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class BrokerNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str

class ReplicaNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class MessageSizeTooLargeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class StaleControllerEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str

class OffsetMetadataTooLargeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class NetworkExceptionError(BrokerResponseError):
    errno: int
    message: str
    retriable: bool
    invalid_metadata: bool

class CoordinatorLoadInProgressError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class CoordinatorNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class NotCoordinatorError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidTopicError(BrokerResponseError):
    errno: int
    message: str
    description: str

class RecordListTooLargeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class NotEnoughReplicasError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class NotEnoughReplicasAfterAppendError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidRequiredAcksError(BrokerResponseError):
    errno: int
    message: str
    description: str

class IllegalGenerationError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InconsistentGroupProtocolError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidGroupIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownMemberIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidSessionTimeoutError(BrokerResponseError):
    errno: int
    message: str
    description: str

class RebalanceInProgressError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidCommitOffsetSizeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class TopicAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str

class GroupAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str

class ClusterAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str

class InvalidTimestampError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnsupportedSaslMechanismError(BrokerResponseError):
    errno: int
    message: str
    description: str

class IllegalSaslStateError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnsupportedVersionError(BrokerResponseError):
    errno: int
    message: str
    description: str

class TopicAlreadyExistsError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidPartitionsError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidReplicationFactorError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidReplicationAssignmentError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidConfigurationError(BrokerResponseError):
    errno: int
    message: str
    description: str

class NotControllerError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidRequestError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnsupportedForMessageFormatError(BrokerResponseError):
    errno: int
    message: str
    description: str

class PolicyViolationError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class OutOfOrderSequenceNumberError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DuplicateSequenceNumberError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidProducerEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidTxnStateError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidProducerIdMappingError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidTransactionTimeoutError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ConcurrentTransactionsError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class TransactionCoordinatorFencedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class TransactionalIdAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str
    retriable: bool

class SecurityDisabledError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class OperationNotAttemptedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class KafkaStorageError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class LogDirNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class SaslAuthenticationFailedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnknownProducerIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ReassignmentInProgressError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DelegationTokenAuthDisabledError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DelegationTokenNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DelegationTokenOwnerMismatchError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DelegationTokenRequestNotAllowedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DelegationTokenAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str
    retriable: bool

class DelegationTokenExpiredError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidPrincipalTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class NonEmptyGroupError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class GroupIdNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FetchSessionIdNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidFetchSessionEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ListenerNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class TopicDeletionDisabledError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FencedLeaderEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class UnknownLeaderEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class UnsupportedCompressionTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class StaleBrokerEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class OffsetNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class MemberIdRequiredError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class PreferredLeaderNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class GroupMaxSizeReachedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FencedInstanceIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class EligibleLeadersNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class ElectionNotNeededError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class NoReassignmentInProgressError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class GroupSubscribedToTopicError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidRecordError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnstableOffsetCommitError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ThrottlingQuotaExceededError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ProducerFencedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ResourceNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DuplicateResourceError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnacceptableCredentialError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InconsistentVoterSetError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidUpdateVersionError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FeatureUpdateFailedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class PrincipalDeserializationFailureError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class SnapshotNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class PositionOutOfRangeError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnknownTopicIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class DuplicateBrokerRegistrationError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class BrokerIdNotRegisteredError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InconsistentTopicIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool
    invalid_metadata: bool

class InconsistentClusterIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class TransactionalIdNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FetchSessionTopicIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class IneligibleReplicaError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class NewLeaderElectedError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class OffsetMovedToTieredStorageError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FencedMemberEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnreleasedInstanceIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnsupportedAssignorError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class StaleMemberEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class MismatchedEndpointTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnsupportedEndpointTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnknownControllerIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class UnknownSubscriptionIdError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class TelemetryTooLargeError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidRegistrationError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class TransactionAbortableError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidRecordStateError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class ShareSessionNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidShareSessionEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class FencedStateEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class InvalidVoterKeyError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class DuplicateVoterError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

class VoterNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str
    retriable: bool

kafka_errors: Incomplete

def for_code(error_code): ...
