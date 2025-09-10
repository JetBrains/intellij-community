from logging import Logger

log: Logger

class TargetPoller:
    def __init__(self, cache, rule_poller, connector) -> None: ...
    def start(self) -> None: ...
