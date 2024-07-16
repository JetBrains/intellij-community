// WITH_STDLIB
@RequiresOptIn(message = "This API is experimental. It may be changed in the future without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class MyOptInAnnotation // Opt-in requirement annotation

@MyOptInAnnotation
data class DataClass (val value1: Int, val value2: Int) // A class requiring opt-in

@MyOptInAnnotation
fun getDate() {
    val dateProvider: DataClass = DataClass(42, 42)
    val value2 = <selection>dateProvider.value2</selection>
}

// IGNORE_K1