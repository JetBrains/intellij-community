from enum import Enum

class ExchangeType(Enum):
    direct = "direct"
    fanout = "fanout"
    headers = "headers"
    topic = "topic"
