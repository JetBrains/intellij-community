// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package destructurableClasses

data class A(val x: Int, val y: Int)

class B {
    operator fun component1() = 1
    operator fun component2() = 1
    operator fun component3() = 1
    operator fun component4() = 1
}
