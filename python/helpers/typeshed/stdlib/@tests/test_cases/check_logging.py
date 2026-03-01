from __future__ import annotations

import logging
import logging.handlers
import multiprocessing
import queue
from typing import Any

# This pattern comes from the logging docs, and should therefore pass a type checker
# See https://docs.python.org/3/library/logging.html#logrecord-objects

old_factory = logging.getLogRecordFactory()


def record_factory(*args: Any, **kwargs: Any) -> logging.LogRecord:
    record = old_factory(*args, **kwargs)
    record.custom_attribute = 0xDECAFBAD
    return record


logging.setLogRecordFactory(record_factory)

# The logging docs say that QueueHandler and QueueListener can take "any queue-like object"
# We test that here (regression test for #10168)
logging.handlers.QueueHandler(queue.Queue())
logging.handlers.QueueHandler(queue.SimpleQueue())
logging.handlers.QueueHandler(multiprocessing.Queue())
logging.handlers.QueueListener(queue.Queue())
logging.handlers.QueueListener(queue.SimpleQueue())
logging.handlers.QueueListener(multiprocessing.Queue())

# These all raise at runtime.
logging.basicConfig(filename="foo.log", handlers=[])  # type: ignore
logging.basicConfig(filemode="w", handlers=[])  # type: ignore
logging.basicConfig(stream=None, handlers=[])  # type: ignore
logging.basicConfig(filename="foo.log", stream=None)  # type: ignore
logging.basicConfig(filename=None, stream=None)  # type: ignore
# These are ok.
logging.basicConfig()
logging.basicConfig(handlers=[])
logging.basicConfig(filename="foo.log", filemode="w")
logging.basicConfig(filename="foo.log", filemode="w", handlers=None)
logging.basicConfig(stream=None)
logging.basicConfig(stream=None, handlers=None)
# dubious but accepted, has same meaning as 'stream=None'.
logging.basicConfig(filename=None)
# These are technically accepted at runtime, but are forbidden in the stubs to help
# prevent user mistakes. Passing 'filemode' / 'encoding' / 'errors' does nothing
# if 'filename' is not specified.
logging.basicConfig(stream=None, filemode="w")  # type: ignore
logging.basicConfig(stream=None, encoding="utf-8")  # type: ignore
logging.basicConfig(stream=None, errors="strict")  # type: ignore
logging.basicConfig(handlers=[], encoding="utf-8")  # type: ignore
logging.basicConfig(handlers=[], errors="strict")  # type: ignore
