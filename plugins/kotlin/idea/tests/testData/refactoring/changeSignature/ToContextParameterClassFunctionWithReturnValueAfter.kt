// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class MyClass {
    val value: Int = 42

    context(param: String) fun getLength(): Int = param.length

    fun inside(param: Int = with("abc") {
        getLength()
    }, param2: Int = with("abcd") {
        this@MyClass.getLength()
    }
    ) {
        with(with("param") {
            getLength()
        }) {
            val p: Int = this + 1
        }
        val sum = with("abc") {
            getLength()
        }.plus(with("abcd") {
            getLength()
        })
        println(with("abc") {
            getLength()
        })
    }

    fun inside2(another: MyClass, param: Int = with("abc") {
        another.getLength()
    }
    ) {
        with("param") {
            another.getLength()
        }
    }
}

fun MyClass.foo() = with("param1") {
    getLength()
}

fun String.bar(m: MyClass) = with(this) {
    m.getLength()
}
