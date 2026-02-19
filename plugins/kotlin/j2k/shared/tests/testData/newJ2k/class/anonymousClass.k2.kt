// !ADD_JAVA_API
package test

import javaApi.Listener

class Test {
    private val listener: Listener = object : Listener {
        override fun onChange(visibility: Int) {
            val a = (visibility and 1)
        }
    }
}
