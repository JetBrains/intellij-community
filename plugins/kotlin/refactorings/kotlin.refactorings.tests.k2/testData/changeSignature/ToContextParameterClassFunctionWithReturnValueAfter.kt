// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    context(param: String)
    fun getLength(): Int = param.length

    fun inside(param: Int = context("abc") {
        getLength()
    }, param2: Int = context("abcd") {
        this@MyClass.getLength()
    }
    ) {
        with(context("param") {
            getLength()
        }) {
            val p: Int = this + 1
        }
        val sum = context("abc") {
            getLength()
        }.plus(context("abcd") {
            getLength()
        })
        println(context("abc") {
            getLength()
        })
    }

    fun inside2(another: MyClass, param: Int = context("abc") {
        another.getLength()
    }
    ) {
        context("param") {
            another.getLength()
        }
    }
}

fun MyClass.foo() = context("param1") {
    getLength()
}

fun String.bar(m: MyClass) = m.getLength()
