// NEW_NAME: m
// RENAME: member
package test

private val m = ""

private class A {
    private val <caret>a = ""
    fun mm() {
        println(m)
    }
}