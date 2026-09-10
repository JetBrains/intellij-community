package pack2

import pack.helper

object Local {
    const val CONST: Int = 1
}

fun use() {
    helper(Local.CONST)
}
