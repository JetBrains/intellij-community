// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

import types.TypeA
import types.TypeB

// TypeA is imported, so the first pass already imports ops1.get. TypeD is not, so d[b] waits for the second pass.
fun case12(a: TypeA, d: TypeD, b: TypeB) {
    a[b]
    d[b]
}
