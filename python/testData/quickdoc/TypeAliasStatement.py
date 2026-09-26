type MyDict[T: int = str, U: str = int, *Ts = *tuple[int], **P = [int, str]] = dict[T, U]

#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

x: MyD<the_ref>ict