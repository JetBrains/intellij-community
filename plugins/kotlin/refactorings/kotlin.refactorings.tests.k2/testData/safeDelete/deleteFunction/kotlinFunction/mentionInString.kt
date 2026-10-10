// SAFE_DELETE_OPTIONS: searchInComments=true
package test

fun <caret>gone() {}

fun f() {
    println("call gone() here")
}
