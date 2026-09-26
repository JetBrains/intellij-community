from builtins import list as _list
from typing import ClassVar

from .alter import AlterConfigs as AlterConfigs
from .describe import DescribeConfigs as DescribeConfigs
from .list import ListConfigResources as ListConfigResources
from .reset import ResetConfigs as ResetConfigs

class ConfigsCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[_list[type[DescribeConfigs | AlterConfigs | ListConfigResources | ResetConfigs]]]
