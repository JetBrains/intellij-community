// NEW_NAME: m
// RENAME: member
package test

private fun <caret>a() = ""

private class A {
    private fun m() = ""
    fun mm() {
        println(a())
    }
}