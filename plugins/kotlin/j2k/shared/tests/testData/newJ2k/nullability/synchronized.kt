class J {
    fun foo(notNull: Any) {
        synchronized(notNull) {}
    }
}
