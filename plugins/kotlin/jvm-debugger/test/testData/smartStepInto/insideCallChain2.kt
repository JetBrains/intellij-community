fun String.convert(function: (String) -> String) = function("convert: $this")
fun foo() = "three"
fun String.convert2(function: (String) -> String) = function("convert: $this")
fun foo2() = "three"

fun main() {
    "one".convert { "two $it" }
        <caret>.convert { foo() }
        .convert2 { foo2() }
}

// EXISTS: convert((String) -> String), convert: function.invoke(), convert2((String) -> String), convert2: function.invoke()
// IGNORE_K2
