// "Specify return type explicitly" "false"
// ERROR: Function 'foo' without a body must be abstract
// ACTION: Add function body
// ACTION: Convert member to extension
// ACTION: Make 'foo' 'abstract'
// K2_AFTER_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY
// K2_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY

package a

class A() {
    public fun <caret>foo()
}