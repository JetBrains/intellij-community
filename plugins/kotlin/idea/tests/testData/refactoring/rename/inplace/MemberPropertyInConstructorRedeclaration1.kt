// NEW_NAME: p
// RENAME: member
// SHOULD_FAIL_WITH: Parameter 'p' is already declared in class 'A'
open class A(val p: String) {

}
class B : A("") {
    val p<caret>1 = ""
}