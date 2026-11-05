// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package ops1

import types.TypeA
import types.TypeB
import types.TypeC
import types.TypeD
import types.TypeE
import types.TypeF
import types.TypeG

operator fun TypeA.get(b: TypeB): TypeC = TypeC()

operator fun TypeA.set(b: TypeB, c: TypeC): TypeC = c

operator fun TypeA.component1(): TypeC = TypeC()

operator fun TypeA.contains(b: TypeB): Boolean = true

operator fun TypeA.rangeTo(other: TypeA): TypeC = TypeC()

operator fun TypeA.rangeUntil(other: TypeA): TypeC = TypeC()

operator fun TypeA.plus(other: TypeA): TypeA = other

operator fun TypeA.unaryMinus(): TypeA = this

operator fun TypeA.not(): Boolean = true

operator fun TypeA.inc(): TypeA = this

operator fun TypeA.invoke(): TypeC = TypeC()

infix fun TypeA.split(b: TypeB): TypeC = TypeC()

operator fun TypeD.iterator(): TypeE = TypeE()

operator fun TypeF.getValue(thisRef: Any?, property: Any?): TypeC = TypeC()

operator fun TypeG.get(b: TypeB): TypeC = TypeC()
