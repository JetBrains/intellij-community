fun <caret>foo(receiver: String, value: Int) {}

val reference: (String, Int) -> Unit = ::foo
