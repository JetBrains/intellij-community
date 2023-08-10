import logging
from typing import Any

# This pattern comes from the logging docs, and should therefore pass a type checker
# See https://docs.python.org/3/library/logging.html#logrecord-objects

old_factory = logging.getLogRecordFactory()


def record_factory(*args: Any, **kwargs: Any) -> logging.LogRecord:
    record = old_factory(*args, **kwargs)
    record.custom_attribute = 0xDECAFBAD
    return record


logging.setLogRecordFactory(record_factory)
