#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class C[*Ts: [int] = [bool]]:
    def f(self) -> tuple[*T<the_ref>s]: ...