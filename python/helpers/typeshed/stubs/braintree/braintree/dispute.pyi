from _typeshed import Incomplete
from decimal import Decimal
from typing import Final

from braintree.attribute_getter import AttributeGetter
from braintree.dispute_details import DisputeEvidence, DisputePayPalMessage, DisputeStatusHistory
from braintree.transaction_details import TransactionDetails

class Dispute(AttributeGetter):
    class Status:
        Accepted: Final = "accepted"
        AutoAccepted: Final = "auto_accepted"
        Disputed: Final = "disputed"
        Expired: Final = "expired"
        Lost: Final = "lost"
        Open: Final = "open"
        UnderReview: Final = "under_review"
        Won: Final = "won"

    class Reason:
        CancelledRecurringTransaction: Final = "cancelled_recurring_transaction"
        CreditNotProcessed: Final = "credit_not_processed"
        Duplicate: Final = "duplicate"
        Fraud: Final = "fraud"
        General: Final = "general"
        InvalidAccount: Final = "invalid_account"
        NotRecognized: Final = "not_recognized"
        ProductNotReceived: Final = "product_not_received"
        ProductUnsatisfactory: Final = "product_unsatisfactory"
        Retrieval: Final = "retrieval"
        TransactionAmountDiffers: Final = "transaction_amount_differs"

    class Kind:
        Chargeback: Final = "chargeback"
        PreArbitration: Final = "pre_arbitration"
        Retrieval: Final = "retrieval"

    class ChargebackProtectionLevel:
        Effortless: Final = "effortless"
        Standard: Final = "standard"
        NotProtected: Final = "not_protected"

    class PreDisputeProgram:
        NONE: Final = "none"
        VisaRdr: Final = "visa_rdr"

    class ProtectionLevel:
        EffortlessCBP: Final = "Effortless Chargeback Protection tool"
        StandardCBP: Final = "Chargeback Protection tool"
        NoProtection: Final = "No Protection"

    @staticmethod
    def accept(id): ...
    @staticmethod
    def add_file_evidence(dispute_id, document_upload_id): ...
    @staticmethod
    def add_text_evidence(id, content_or_request): ...
    @staticmethod
    def finalize(id): ...
    @staticmethod
    def find(id): ...
    @staticmethod
    def remove_evidence(id, evidence_id): ...
    @staticmethod
    def search(*query): ...
    amount: Decimal | None
    amount_disputed: Decimal | None
    amount_won: Decimal | None
    protection_level: Incomplete
    transaction_details: TransactionDetails
    transaction = transaction_details
    evidence: list[DisputeEvidence] | None
    paypal_messages: list[DisputePayPalMessage] | None
    status_history: list[DisputeStatusHistory] | None
    processor_comments: Incomplete
    forwarded_comments: processor_comments
    def __init__(self, attributes) -> None: ...
