import asyncio as aio
import datetime
from collections import OrderedDict as ODict
from typing import Text, TypeAlias, Set


def test():
    a: 'Text' = "a"
    b: 'aio.Condition' = aio.Condition()
    c: 'datetime.timedelta' = datetime.timedelta(0)
    d: 'ODict' = ODict()
    S: TypeAlias = 'Set'
