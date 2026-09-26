@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
annotation class NonNls

internal interface I {
    companion object {
        @NonNls
        const val str: String = "hello"
    }
}

internal object C {
    @NonNls
    const val BLADE: String = "Blade"
}
