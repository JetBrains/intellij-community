// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package types

class TypeA

class TypeB

class TypeC

class TypeD

class TypeE {
    operator fun hasNext(): Boolean = false

    operator fun next(): TypeC = TypeC()
}

class TypeF

class TypeG

class TypeH
