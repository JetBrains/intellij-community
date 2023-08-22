package demo

internal class Test {
    fun test() {
        var name = "$$$$"
        name = name.replace("\\$[0-9]+".toRegex(), "\\$ \\\$a \${a} \\\${a}")
        val c = '$'
        println(c)
        val C = '$'
        println(C)
    }
}
