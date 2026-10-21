from __future__ import annotations

from typing import Optional
from typing_extensions import assert_type

import greenlet

g = greenlet.greenlet()
h = greenlet.greenlet()
assert_type(g.parent, Optional[greenlet.greenlet])
g.parent = h

# Although "parent" sometimes can be None at runtime,
# it's always illegal for it to be set to None
g.parent = None  # type: ignore
