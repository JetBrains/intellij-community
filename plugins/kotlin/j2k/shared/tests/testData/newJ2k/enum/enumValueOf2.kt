object J {
    fun <T : Enum<T?>?> crash(clazz: Class<T?>) {
        java.lang.Enum.valueOf<T?>(clazz, "X")
    }
}
