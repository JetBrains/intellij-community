// "Specify return type explicitly" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=strict
// ERROR: Visibility must be specified in explicit API mode
// ERROR: Visibility must be specified in explicit API mode
package a

interface A {
    fun <caret>foo() = ""
}
