class KotlinClass {
    fun <caret>a(): Int = this.extension()
}

fun test() {
    KotlinClass().a()
}

fun KotlinClass.extension(): Int = 42
