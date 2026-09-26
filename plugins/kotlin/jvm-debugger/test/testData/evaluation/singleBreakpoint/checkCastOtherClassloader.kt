import java.io.IOException

fun main() {
    val c1 = Class.forName(CastLoaders::class.java.name, true, MyClassloader()).newInstance()
    //Breakpoint!
    val a = 1
}

val NAME: String = CastLoaders::class.java.name

internal class MyClassloader : ClassLoader() {
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*> {
        if (name != NAME) {
            return super.loadClass(name)
        }
        try {
            val `in` = getSystemResourceAsStream(NAME.replace('.', '/') + ".class")
            val a = ByteArray(10000)
            val len = `in`!!.read(a)
            `in`.close()
            return defineClass(name, a, 0, len)
        } catch (e: IOException) {
            throw ClassNotFoundException()
        }
    }
}

class CastLoaders

// EXPRESSION: c1 is CastLoaders
// RESULT: 0: Z

// EXPRESSION: c1 as CastLoaders
// RESULT: java.lang.ClassCastException : CastLoaders cannot be cast to CastLoaders
