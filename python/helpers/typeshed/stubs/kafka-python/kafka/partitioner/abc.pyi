import abc

from kafka.cluster import ClusterMetadata

class Partitioner(abc.ABC):
    @abc.abstractmethod
    def partition(
        self, topic: str, key, serialized_key: bytes, value, serialized_value: bytes, cluster: ClusterMetadata
    ) -> int: ...
