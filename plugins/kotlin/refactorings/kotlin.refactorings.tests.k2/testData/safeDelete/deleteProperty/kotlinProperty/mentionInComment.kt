// SAFE_DELETE_OPTIONS: searchInComments=true
package test

class A {
    val <caret>gone = 1

    // gone was the old value
    fun f() {}
}
