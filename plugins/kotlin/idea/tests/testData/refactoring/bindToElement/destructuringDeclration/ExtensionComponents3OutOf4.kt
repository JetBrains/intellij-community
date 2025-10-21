// FILE: test/Destruct.kt
// BIND_TO test.bar.component3
package test

import test.foo.component1
import test.foo.component2
import test.foo.component3
import test.foo.component4
import test.boo.MovingComponentExtended

fun referComponentExt() {
    val (a, b, <caret>c) = MovingComponentExtended()
}

// FILE: test/MovingComponentExtended.kt
package test.boo

class MovingComponentExtended

// FILE: test/foo/A.kt
package test.foo

import test.boo.MovingComponentExtended

operator fun MovingComponentExtended.component1() = 1
operator fun MovingComponentExtended.component2() = 2
operator fun MovingComponentExtended.component3() = 3
operator fun MovingComponentExtended.component4() = 4

// FILE: test/bar/A.kt
package test.bar

import test.boo.MovingComponentExtended

operator fun MovingComponentExtended.component3() = 2
