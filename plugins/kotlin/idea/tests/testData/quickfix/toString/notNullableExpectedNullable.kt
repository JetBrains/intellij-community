// "Add 'toString()' call" "true"
// ACTION: Add 'toString()' call
// ACTION: Change parameter 'a' type of function 'bar' to 'Any'
// ACTION: Create function 'bar'

fun foo() {
    bar(Any()<caret>)
}

fun bar(a: String?) {
}