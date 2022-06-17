
fun foo(bar: String): String? = null

fun bar() = 42

fun Int.localToString(): String = ""

fun baz(): String? {
    return foo("Lorem ipsum") ?: foo("dolor sit amet") ?: bar().localToString()
}
