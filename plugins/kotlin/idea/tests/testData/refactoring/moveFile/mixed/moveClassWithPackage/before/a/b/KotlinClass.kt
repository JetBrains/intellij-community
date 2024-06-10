package a.b

import a.u.JavaUsage
import a.u.KotlinUsage

class KotlinClass {
    fun foo() {
        val javaUsage = JavaUsage()
        val kotlinUsage = KotlinUsage()
    }
}