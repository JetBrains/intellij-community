// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Anno

context(@Anno <caret>a: Boolean, @Anno b: Int)
fun foo(@Anno c: String, @Anno d: Double) {
}