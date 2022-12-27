// WITH_STDLIB
// PARAM_DESCRIPTOR: val parameters: kotlin.collections.List<kotlin.Int> defined in applyTo
// PARAM_TYPES: kotlin.collections.List<kotlin.Int>, kotlin.collections.Collection<kotlin.Int>, kotlin.collections.Iterable<kotlin.Int>

class KtNamedFunction {
    val valueParameters: List<Int> = listOf()
}

fun applyTo(element: KtNamedFunction) {
    val parameters = element.valueParameters
    <selection>parameters.forEach { parameter -> parameter }</selection>
}