// "Specify return type explicitly" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=strict
package a

interface A {
    fun <caret>foo() = ""
}
