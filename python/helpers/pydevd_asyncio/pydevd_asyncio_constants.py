import os
from _pydevd_bundle.pydevd_constants import IS_PY38_OR_GREATER

IS_ASYNCIO_DEBUGGER_ENV = os.getenv('ASYNCIO_DEBUGGER_ENV') == 'True' and IS_PY38_OR_GREATER
