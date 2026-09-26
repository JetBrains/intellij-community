from _typeshed import Incomplete

class KafkaManagerMetrics:
    metrics: Incomplete
    connection_closed: Incomplete
    connection_created: Incomplete
    def __init__(self, metrics, metric_group_prefix: str, conns) -> None: ...

class KafkaConnectionMetrics:
    metrics: Incomplete
    bytes_sent: Incomplete
    bytes_received: Incomplete
    request_time: Incomplete
    throttle_time: Incomplete
    def __init__(self, metrics, metric_group_prefix: str, node_id) -> None: ...
