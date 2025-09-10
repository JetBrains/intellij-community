#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

from ._compat_py3k import asynccontextmanager as asynccontextmanager
from ._concurrency_py3k import (
    AsyncAdaptedLock as AsyncAdaptedLock,
    await_fallback as await_fallback,
    await_only as await_only,
    greenlet_spawn as greenlet_spawn,
    is_exit_exception as is_exit_exception,
)

have_greenlet: bool
asyncio: Any | None
