fun foo() {
    ba<caret>r()
}

fun bar(s: List<String> = emptyList()) {
    println(s)
}