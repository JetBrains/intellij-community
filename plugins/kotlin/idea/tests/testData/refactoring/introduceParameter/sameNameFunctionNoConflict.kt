fun String.bar() = 0
class Foo {
    fun bar(): String {
        return <selection>"bar"</selection>
    }
}

// IGNORE_K1