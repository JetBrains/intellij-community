// NEW_NAME: m
// RENAME: member
package test

private val <caret>a = ""

private class A {
    private val m = ""
    fun mm() {
        println(a)
    }
}