@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
annotation class NonNls

interface I {
    companion object {
        const val str: @NonNls String = "hello"
    }
}
