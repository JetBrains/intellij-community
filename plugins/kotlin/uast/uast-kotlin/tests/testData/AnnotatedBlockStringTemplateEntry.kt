fun test(expr: Any) {
    val v1 = "${@Suppress expr}"
    val v2 = "${@Suppress("foo") expr}"
    val v3 = "${@Suppress("foo") "${@Suppress("bar") expr}"}"
    val v4 = "Start ${@Suppress("foo") expr} end"
    val v5 = $$$"Start $$${@Suppress("foo") expr}"
    val v6 = "${@Suppress 1 + 2}"
    val v7 = "${}"
}
