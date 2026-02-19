// NEW_NAME: m
// RENAME: member
package test

private fun m() = ""

private class A {
    private fun <caret>a() = ""
    fun mm() {
        println(m())
    }
}