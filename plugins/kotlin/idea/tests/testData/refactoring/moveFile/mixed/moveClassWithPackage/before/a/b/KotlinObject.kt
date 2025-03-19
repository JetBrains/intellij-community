package a.b

import a.u.JavaUsage
import a.u.KotlinUsage

object KotlinObject {
    fun foo() {
        val javaUsage = JavaUsage()
        val kotlinUsage = KotlinUsage()
    }
}