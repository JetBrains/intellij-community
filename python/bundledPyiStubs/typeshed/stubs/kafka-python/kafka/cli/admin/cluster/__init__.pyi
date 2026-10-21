from typing import ClassVar

from .describe import DescribeCluster as DescribeCluster
from .describe_quorum import DescribeQuorum as DescribeQuorum
from .features import DescribeFeatures as DescribeFeatures, UpdateFeatures as UpdateFeatures
from .log_dirs import AlterLogDirs as AlterLogDirs, DescribeLogDirs as DescribeLogDirs
from .versions import GetApiVersions as GetApiVersions, GetBrokerVersion as GetBrokerVersion

class ClusterCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[
        list[
            type[
                DescribeCluster
                | DescribeQuorum
                | GetApiVersions
                | GetBrokerVersion
                | DescribeFeatures
                | UpdateFeatures
                | DescribeLogDirs
                | AlterLogDirs
            ]
        ]
    ]
