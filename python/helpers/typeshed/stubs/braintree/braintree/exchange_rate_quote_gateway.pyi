from _typeshed import Incomplete

class ExchangeRateQuoteGateway:
    gateway: Incomplete
    config: Incomplete
    graphql_client: Incomplete
    def __init__(self, gateway, graphql_client: Incomplete | None = None) -> None: ...
    exchange_rate_quote_payload: Incomplete
    def generate(self, request): ...
