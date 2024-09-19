from _typeshed import Incomplete
from typing import Final

from braintree.resource import Resource

class DocumentUpload(Resource):
    class Kind:
        EvidenceDocument: Final = "evidence_document"

    @staticmethod
    def create(params: Incomplete | None = None): ...
    @staticmethod
    def create_signature(): ...
    def __init__(self, gateway, attributes) -> None: ...
