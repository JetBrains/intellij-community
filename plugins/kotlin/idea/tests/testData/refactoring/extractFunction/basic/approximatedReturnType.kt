class SimpleClass

fun findClasses() {
    val classloader = Alias::class.java.classLoader
    val obj = <selection>classloader?.loadClass("")?.kotlin</selection>
}

// IGNORE_K1