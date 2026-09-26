from typing import ClassVar

from .create import CreateACLs as CreateACLs
from .delete import DeleteACLs as DeleteACLs
from .describe import DescribeACLs as DescribeACLs

class ACLsCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[list[type[CreateACLs | DeleteACLs | DescribeACLs]]]
