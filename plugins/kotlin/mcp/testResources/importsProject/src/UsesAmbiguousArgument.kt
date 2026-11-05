// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package sample

class TypeI

class TypeJ

fun target(value: TypeI) {}

fun target(value: TypeJ) {}

fun case11() {
    target(Marker())
}
