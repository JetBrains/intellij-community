from typing import ClassVar

from .alter_reassignments import AlterPartitionReassignments as AlterPartitionReassignments
from .create import CreatePartitions as CreatePartitions
from .delete_records import DeleteRecords as DeleteRecords
from .describe import DescribeTopicPartitions as DescribeTopicPartitions
from .elect_leaders import ElectLeaders as ElectLeaders
from .list_offsets import ListPartitionOffsets as ListPartitionOffsets
from .list_reassignments import ListPartitionReassignments as ListPartitionReassignments

class PartitionsCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[
        list[
            type[
                CreatePartitions
                | DescribeTopicPartitions
                | ListPartitionOffsets
                | ListPartitionReassignments
                | AlterPartitionReassignments
                | DeleteRecords
                | ElectLeaders
            ]
        ]
    ]
