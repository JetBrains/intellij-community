// "Wrap with '?.let { ... }' call" "false"
// WITH_STDLIB
// ACTION: Add non-null asserted (!!) call
// ACTION: Convert to block body
// ACTION: Enable 'Types' inlay hints
// ACTION: Introduce local variable
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type Int?

fun Int.bar() = this

fun foo(arg: Int?) = arg<caret>.bar()