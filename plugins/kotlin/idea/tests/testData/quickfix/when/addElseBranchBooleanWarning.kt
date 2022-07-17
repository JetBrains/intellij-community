// "Add else branch" "true"
fun foo(x: String?) {
    while (true) {
        x ?: when<caret> { true -> break }
    }
}
