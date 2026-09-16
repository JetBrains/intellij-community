package pack

object Local {
    const val CONST: Int = 1
}

fun use() {
    helper(Local.CONST)
}
