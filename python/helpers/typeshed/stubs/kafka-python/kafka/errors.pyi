from _typeshed import Incomplete

class KafkaError(Exception):
    retriable: bool
    invalid_metadata: bool
    def __eq__(self, other): ...

class RetriableError(KafkaError):
    retriable: bool

class InvalidMetadataError(RetriableError):
    invalid_metadata: bool

class Cancelled(RetriableError): ...

class CommitFailedError(KafkaError):
    def __init__(self, *args) -> None: ...

class IllegalArgumentError(KafkaError): ...
class IllegalStateError(KafkaError): ...
class KafkaConfigurationError(KafkaError): ...
class KafkaConnectionError(InvalidMetadataError): ...
class KafkaProtocolError(KafkaError): ...
class CorrelationIdError(RetriableError, KafkaProtocolError): ...
class InvalidReceiveError(KafkaProtocolError): ...
class KafkaTimeoutError(KafkaError): ...
class MetadataEmptyBrokerList(KafkaError): ...
class NoOffsetForPartitionError(KafkaError): ...

class LogTruncationError(KafkaError):
    divergent_offsets: Incomplete
    def __init__(self, divergent_offsets, *args): ...

class NodeNotReadyError(RetriableError): ...
class UnknownBrokerIdError(KafkaError): ...
class QuotaViolationError(KafkaError): ...
class StaleMetadata(InvalidMetadataError): ...
class TooManyInFlightRequests(RetriableError): ...
class UnrecognizedBrokerVersion(KafkaError): ...
class UnsupportedCodecError(KafkaError): ...

class TransactionAbortedError(KafkaError):
    message: str

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

class UnknownTopicOrPartitionError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidFetchRequestError(BrokerResponseError):
    errno: int
    message: str
    description: str

class LeaderNotAvailableError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class NotLeaderForPartitionError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class RequestTimedOutError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class BrokerNotAvailableError(BrokerResponseError):
    errno: int
    message: str
    description: str

class ReplicaNotAvailableError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

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

class NetworkExceptionError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str

class CoordinatorLoadInProgressError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class CoordinatorNotAvailableError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class NotCoordinatorError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidTopicError(BrokerResponseError):
    errno: int
    message: str
    description: str

class RecordListTooLargeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class NotEnoughReplicasError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class NotEnoughReplicasAfterAppendError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

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

class IncompatibleBrokerVersion(UnsupportedVersionError): ...

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

class NotControllerError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

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

class OutOfOrderSequenceNumberError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DuplicateSequenceNumberError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidProducerEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidTxnStateError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidProducerIdMappingError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidTransactionTimeoutError(BrokerResponseError):
    errno: int
    message: str
    description: str

class ConcurrentTransactionsError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class TransactionCoordinatorFencedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class TransactionalIdAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str

class SecurityDisabledError(BrokerResponseError):
    errno: int
    message: str
    description: str

class OperationNotAttemptedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class KafkaStorageError(BrokerResponseError):
    errno: int
    message: str
    description: str

class LogDirNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

class SaslAuthenticationFailedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownProducerIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class ReassignmentInProgressError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DelegationTokenAuthDisabledError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DelegationTokenNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DelegationTokenOwnerMismatchError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DelegationTokenRequestNotAllowedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DelegationTokenAuthorizationFailedError(AuthorizationError):
    errno: int
    message: str
    description: str

class DelegationTokenExpiredError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidPrincipalTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class NonEmptyGroupError(BrokerResponseError):
    errno: int
    message: str
    description: str

class GroupIdNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

class FetchSessionIdNotFoundError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidFetchSessionEpochError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class ListenerNotFoundError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class TopicDeletionDisabledError(BrokerResponseError):
    errno: int
    message: str
    description: str

class FencedLeaderEpochError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownLeaderEpochError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class UnsupportedCompressionTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class StaleBrokerEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str

class OffsetNotAvailableError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class MemberIdRequiredError(BrokerResponseError):
    errno: int
    message: str
    description: str

class PreferredLeaderNotAvailableError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class GroupMaxSizeReachedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class FencedInstanceIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class EligibleLeadersNotAvailableError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class ElectionNotNeededError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class NoReassignmentInProgressError(BrokerResponseError):
    errno: int
    message: str
    description: str

class GroupSubscribedToTopicError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidRecordError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnstableOffsetCommitError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class ThrottlingQuotaExceededError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class ProducerFencedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class ResourceNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DuplicateResourceError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnacceptableCredentialError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InconsistentVoterSetError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidUpdateVersionError(BrokerResponseError):
    errno: int
    message: str
    description: str

class FeatureUpdateFailedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class PrincipalDeserializationFailureError(BrokerResponseError):
    errno: int
    message: str
    description: str

class SnapshotNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

class PositionOutOfRangeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownTopicIdError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class DuplicateBrokerRegistrationError(BrokerResponseError):
    errno: int
    message: str
    description: str

class BrokerIdNotRegisteredError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InconsistentTopicIdError(InvalidMetadataError, BrokerResponseError):
    errno: int
    message: str
    description: str

class InconsistentClusterIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class TransactionalIdNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

class FetchSessionTopicIdError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class IneligibleReplicaError(BrokerResponseError):
    errno: int
    message: str
    description: str

class NewLeaderElectedError(BrokerResponseError):
    errno: int
    message: str
    description: str

class OffsetMovedToTieredStorageError(BrokerResponseError):
    errno: int
    message: str
    description: str

class FencedMemberEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnreleasedInstanceIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnsupportedAssignorError(BrokerResponseError):
    errno: int
    message: str
    description: str

class StaleMemberEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str

class MismatchedEndpointTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnsupportedEndpointTypeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownControllerIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class UnknownSubscriptionIdError(BrokerResponseError):
    errno: int
    message: str
    description: str

class TelemetryTooLargeError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidRegistrationError(BrokerResponseError):
    errno: int
    message: str
    description: str

class TransactionAbortableError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidRecordStateError(BrokerResponseError):
    errno: int
    message: str
    description: str

class ShareSessionNotFoundError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidShareSessionEpochError(RetriableError, BrokerResponseError):
    errno: int
    message: str
    description: str

class FencedStateEpochError(BrokerResponseError):
    errno: int
    message: str
    description: str

class InvalidVoterKeyError(BrokerResponseError):
    errno: int
    message: str
    description: str

class DuplicateVoterError(BrokerResponseError):
    errno: int
    message: str
    description: str

class VoterNotFoundError(BrokerResponseError):
    errno: int
    message: str
    description: str

kafka_errors: Incomplete

def for_code(error_code): ...
