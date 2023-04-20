// "Replace with safe (?.) call" "true"
infix fun Int.add(other: Int?): Int {
    return this + 0
}
fun foo() {
    val name: String? = null

    val x = 5 add name<caret>.length


}
