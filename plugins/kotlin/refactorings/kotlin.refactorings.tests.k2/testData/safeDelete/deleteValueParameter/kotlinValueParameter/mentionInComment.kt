// SAFE_DELETE_OPTIONS: searchInComments=true
package test

// pass the gone value
fun f(<caret>gone: Int) {}

fun use() {
    f(1)
}
