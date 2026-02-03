// FILE: test/Destruct.kt
// BIND_TO test.bar.component1
package test

import test.foo.component1
import test.foo.component2
import test.boo.MovingComponentExtended

fun referComponentExt() {
    val (<caret>u, v) = MovingComponentExtended()
}

// FILE: test/MovingComponentExtended.kt
package test.boo

class MovingComponentExtended

// FILE: test/foo/A.kt
package test.foo

import test.boo.MovingComponentExtended

operator fun MovingComponentExtended.component1() = 1
operator fun MovingComponentExtended.component2() = 2

// FILE: test/bar/A.kt
package test.bar

import test.boo.MovingComponentExtended

operator fun MovingComponentExtended.component1() = 1
operator fun MovingComponentExtended.component2() = 2
