@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
annotation class NonNls

internal interface I {
    companion object {
        @NonNls
        const val str: @NonNls String = "hello"
    }
}

internal object C {
    @NonNls
    const val BLADE: @NonNls String = "Blade"
}
