package b

import c.Foo

/**
 * Usages in this file should be updated after the usages in [Usage] for this test.
 * This makes sure we can do retargeting on broken code because of the [ZDependencyForUsageUpdate.buildFoo] usage not being updated yet.
 */
object ZDependencyForUsageUpdate {
    fun buildFoo(): Foo {
        return Foo(0, 0)
    }
}