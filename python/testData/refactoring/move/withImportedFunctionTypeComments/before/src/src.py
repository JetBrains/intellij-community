import asyncio as aio
import datetime
from typing import Text
from collections import OrderedDict as ODict

def test(s, cond, td):
    # type: (Text, aio.Condition, datetime.timedelta) -> ODict
    return ODict()