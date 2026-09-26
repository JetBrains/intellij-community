from builtins import list as _list
from typing import ClassVar

from .alter_offsets import AlterGroupOffsets as AlterGroupOffsets
from .delete import DeleteGroups as DeleteGroups
from .delete_offsets import DeleteGroupOffsets as DeleteGroupOffsets
from .describe import DescribeGroups as DescribeGroups
from .list import ListGroups as ListGroups
from .list_offsets import ListGroupOffsets as ListGroupOffsets
from .remove_members import RemoveGroupMembers as RemoveGroupMembers
from .reset_offsets import ResetGroupOffsets as ResetGroupOffsets

class GroupsCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[
        _list[
            type[
                ListGroups
                | DescribeGroups
                | DeleteGroups
                | ListGroupOffsets
                | AlterGroupOffsets
                | ResetGroupOffsets
                | DeleteGroupOffsets
                | RemoveGroupMembers
            ]
        ]
    ]
