// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

class TypeK

class TypeL

operator fun TypeK.contains(value: Any?): Boolean = true

operator fun TypeL.contains(value: Any?): Boolean = true

fun case13(a: TypeA, b: TypeB) {
    b in a
}
