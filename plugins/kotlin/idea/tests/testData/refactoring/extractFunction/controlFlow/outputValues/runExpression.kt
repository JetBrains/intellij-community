fun test () {
    val nullable: String? = "1"
    val result = <selection>nullable.run {
        this!!
        toInt()
    }</selection>
    val int: Int = result
}
// IGNORE_K1