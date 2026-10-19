fun <caret>foo(number: Int, text: String) {}

val reference: (Int, String) -> Unit = ::foo
