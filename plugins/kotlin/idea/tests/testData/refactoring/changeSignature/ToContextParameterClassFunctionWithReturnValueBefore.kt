// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    fun get<caret>Length(param: String): Int = param.length

    fun inside(param: Int = getLength("abc"), param2: Int = this.getLength("abcd")) {
        with(getLength("param")) {
            val p: Int = this + 1
        }
        val sum = getLength("abc").plus(getLength("abcd"))
        println(getLength("abc"))
    }

    fun inside2(another: MyClass, param: Int = another.getLength("abc")) {
        another.getLength("param")
    }
}

fun MyClass.foo() = getLength("param1")

fun String.bar(m: MyClass) = m.getLength(this)