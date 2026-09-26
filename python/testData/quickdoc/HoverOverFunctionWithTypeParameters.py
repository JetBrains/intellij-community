#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

def f[T: int = int, *Ts = *tuple[int], **P = [str]](t: T) -> T: ...

<the_ref>f
