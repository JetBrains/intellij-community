// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

fun case1(a: TypeA, b: TypeB, c: TypeC) {
    a[b]
    a[b] = c
    val (x) = a
    a..a
}
