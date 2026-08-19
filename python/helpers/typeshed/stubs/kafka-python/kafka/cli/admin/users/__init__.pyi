from typing import ClassVar

from .alter_user_scram_credentials import AlterUserScramCredentials as AlterUserScramCredentials
from .describe_user_scram_credentials import DescribeUserScramCredentials as DescribeUserScramCredentials

class UsersCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[list[type[DescribeUserScramCredentials | AlterUserScramCredentials]]]
