// PARAM_TYPES: kotlin.String.() -> T & Any
// PARAM_DESCRIPTOR: value-parameter f: kotlin.String.() -> T & Any defined in foo

fun <T> foo(f: String.() -> T & Any): T {
    <selection>while (true) {
        val answer = "Hey!".f()
        return answer
    }</selection>
}