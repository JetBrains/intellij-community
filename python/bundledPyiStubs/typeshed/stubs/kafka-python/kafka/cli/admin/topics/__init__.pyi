from builtins import list as _list
from typing import ClassVar

from .create import CreateTopic as CreateTopic
from .delete import DeleteTopic as DeleteTopic
from .describe import DescribeTopics as DescribeTopics
from .list import ListTopics as ListTopics

class TopicsCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[_list[type[ListTopics | DescribeTopics | CreateTopic | DeleteTopic]]]
