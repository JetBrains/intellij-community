class A {
    open class B<caret> {
        // INFO: {"checked": "true"}
        val i: Int = 0
    }
}