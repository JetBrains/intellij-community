// "Surround with null check" "false"
// ACTION: Add non-null asserted (x!!) call
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Introduce import alias
// ACTION: Introduce local variable
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type Int?

var x: Int? = null

fun foo() {
    x<caret>.hashCode()
}