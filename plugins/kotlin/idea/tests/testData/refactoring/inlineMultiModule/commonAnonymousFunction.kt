// PLATFORM: Common
// FILE: Foo.kt
// MAIN
fun abc() {
    (f<caret>un (it: String): String {
        return it.toString()
    }).invoke("")
}