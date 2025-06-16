// "Change return type of called function 'Foo.Foo' to 'Int'" "false"
// ERROR: Type mismatch: inferred type is Foo but Int was expected
// K2_AFTER_ERROR: Return type mismatch: expected 'Int', actual 'Foo'.
fun interface Foo { fun bar() }

fun test(): Int = Foo<caret> { }