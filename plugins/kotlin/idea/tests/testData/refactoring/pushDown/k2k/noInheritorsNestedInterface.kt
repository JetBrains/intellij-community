class A {
    interface B<caret> {
        // INFO: {"checked": "true"}
        fun foo(): Int = 0
    }
}