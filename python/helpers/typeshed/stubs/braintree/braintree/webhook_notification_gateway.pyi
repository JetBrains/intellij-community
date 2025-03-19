from _typeshed import Incomplete

text_type = str

class WebhookNotificationGateway:
    gateway: Incomplete
    config: Incomplete
    def __init__(self, gateway) -> None: ...
    def parse(self, signature, payload): ...
    def verify(self, challenge): ...
