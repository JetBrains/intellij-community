import MyHelper.longHelper

object MyHelper {
    fun longHelper(long: Long) = long.toString()
}

class Usage {
    fun publicFun() {
        println(privateFun("foo"))
    }

    private fun priv<caret>ateFun(str: String) = longHelper(str.hashCode().toLong())
}
