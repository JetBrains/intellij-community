// KTIJ-29632
package org.test

import org.test.OuterClass.InnerClass

internal class OuterClass {
    internal inner class InnerClass
}

internal class User {
    fun main() {
        val outerObject = OuterClass()
        val innerObject: InnerClass = outerObject.InnerClass()
    }
}
