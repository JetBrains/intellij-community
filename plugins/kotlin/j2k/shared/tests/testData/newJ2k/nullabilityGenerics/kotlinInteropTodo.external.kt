import java.util.ArrayList

class K {
    fun return1(): ArrayList<String?>? = null
    fun return2(): ArrayList<String>? = null
    fun return3(): ArrayList<String?> = ArrayList()
    fun return4(): ArrayList<String> = ArrayList()

    fun argument1(x: ArrayList<String?>?) {}
    fun argument2(x: ArrayList<String>?) {}
    fun argument3(x: ArrayList<String?>) {}
    fun argument4(x: ArrayList<String>) {}
}