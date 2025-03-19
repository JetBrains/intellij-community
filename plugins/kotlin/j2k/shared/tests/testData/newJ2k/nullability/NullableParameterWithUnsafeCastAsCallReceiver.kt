internal object Foo {
    fun foo(obj: Any?) {
        val result = (obj as Int).compareTo(123)
    }
}
