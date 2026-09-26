from kafka.protocol.api_data import ApiData

class StickyAssignorUserData(ApiData):
    def __init__(self, *args, **kw) -> None: ...
    # TODO: Reflect TopicPartition, generation, previous_assignment attributes
    def __getattr__(self, name: str): ...  # incomplete class
