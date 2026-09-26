// NEW_NAME: field
// RENAME: member
class Foo(
    val my<caret>Field: String,
)

fun use(field: String) {
    Foo(myField = field)
}