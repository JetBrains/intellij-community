from builtins import list as _list
from typing import ClassVar

from .abort import AbortTransaction as AbortTransaction
from .describe import DescribeTransactions as DescribeTransactions
from .describe_producers import DescribeProducers as DescribeProducers
from .find_hanging import FindHangingTransactions as FindHangingTransactions
from .list import ListTransactions as ListTransactions

class TransactionsCommandGroup:
    GROUP: ClassVar[str]
    HELP: ClassVar[str]
    COMMANDS: ClassVar[
        _list[type[ListTransactions | DescribeTransactions | DescribeProducers | FindHangingTransactions | AbortTransaction]]
    ]
