// "Change return type of called function 'Foo.Foo' to 'Int'" "false"
// ERROR: Type mismatch: inferred type is Foo but Int was expected
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
fun interface Foo { fun bar() }

fun test(): Int = Foo<caret> { }